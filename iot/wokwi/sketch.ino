#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ---------- Pines ----------
#define PIN_DHT      15
#define PIN_PESO     34   // ADC: potenciómetro simula la celda de carga
#define PIN_PIR      13
#define PIN_PUERTA   14   // INPUT_PULLUP: LOW = abierta
#define PIN_LED       2
#define PIN_BUZZER    4

// ---------- Red / MQTT ----------
const char* WIFI_SSID = "Wokwi-GUEST";
const char* WIFI_PASS = "";
const char* MQTT_HOST = "broker.hivemq.com";   
const int   MQTT_PORT = 1883;
const char* BASE      = "losandes/utpgrupo03";        

// ---------- Parámetros ----------
const float TEMP_VITRINA_MAX   = 25.0;   // °C
const float HUM_VITRINA_MAX    = 70.0;   // %
const float PESO_UNITARIO_G    = 60.0;   // g por pan
const int   STOCK_MIN_UNIDADES = 10;     // unidades

// Mapeo del potenciómetro y umbral de peso
const float PESO_MIN_G         = 0.0;    // peso mínimo simulado
const float PESO_MAX_G         = 5000.0; // peso máximo simulado
const float PESO_ALERTA_G      = 1500.0; // LED encendido si peso < este valor

const unsigned long PUERTA_MAX_MS = 10000;    // alerta si la puerta sigue abierta
const unsigned long PIR_DEBOUNCE_MS = 3000;   // antirrebote del PIR
const unsigned long INTERVALO_TELEMETRIA_MS = 5000;

DHT dht(PIN_DHT, DHT22);
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

unsigned long tTelemetria = 0, tPuertaAbierta = 0, tUltimoCliente = 0;
bool puertaAbierta = false, alertaPuertaEnviada = false;
int clientes = 0;

// Últimos valores leídos (para el control del LED)
float pesoActual = 0.0;
float tempActual = NAN;
float humActual  = NAN;

// Slots de alerta: 0 = temp vitrina, 1 = humedad, 2 = puerta
String ultimaAlerta[3] = {"", "", ""};

// Bandera para no repetir la alerta de stock hasta que se recupere
bool stockEnviado = false;

void conectarWifi() {
  Serial.print("Conectando WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASS, 6);
  while (WiFi.status() != WL_CONNECTED) { delay(300); Serial.print("."); }
  Serial.println(" OK");
}

void conectarMqtt() {
  while (!mqtt.connected()) {
    String id = "esp32-losandes-" + String((uint32_t)esp_random(), HEX);
    Serial.print("Conectando MQTT... ");
    String lwt = String(BASE) + "/estado";
    if (mqtt.connect(id.c_str(), nullptr, nullptr, lwt.c_str(), 1, true, "offline")) {
      mqtt.publish(lwt.c_str(), "online", true);
      Serial.println("OK");
    } else {
      Serial.printf("fallo rc=%d, reintento en 2 s\n", mqtt.state());
      delay(2000);
    }
  }
}

// ------------------------------------------------------------------
// Publica por MQTT (JSON compacto) y muestra en el terminal (formato)
// ------------------------------------------------------------------
void publicar(const char* subtopico, JsonDocument& doc) {
  // 1) Enviar JSON COMPACTO por MQTT
  char buf[384];
  serializeJson(doc, buf);
  String topic = String(BASE) + "/" + subtopico;
  bool ok = mqtt.publish(topic.c_str(), buf);

  // 2) Imprimir en el terminal con formato legible
  Serial.println("---------------------------------------------");

  if (strcmp(subtopico, "telemetria") == 0) {
    Serial.println("TELEMETRIA");
    Serial.print("   Vitrina: ");
    Serial.print((float)doc["vitrina_temp_c"], 1);
    Serial.print(" C  |  Humedad: ");
    Serial.print((float)doc["vitrina_hum_pct"], 1);
    Serial.println(" %");

    Serial.print("   Bandeja: ");
    Serial.print((int)doc["bandeja_peso_g"]);
    Serial.print(" g  ->  ");
    Serial.print((int)doc["bandeja_unidades"]);
    Serial.println(" panes");

    Serial.print("   Puerta: ");
    Serial.println(doc["puerta_abierta"] ? "ABIERTA" : "cerrada");

    Serial.print("   Clientes: ");
    Serial.print((int)doc["clientes_acumulados"]);
    Serial.print("   RSSI: ");
    Serial.print((int)doc["wifi_rssi"]);
    Serial.print(" dBm   Uptime: ");
    Serial.print((int)doc["uptime_s"]);
    Serial.println(" s");
  }
  else if (strcmp(subtopico, "eventos") == 0) {
    Serial.print("EVENTO: ");
    Serial.println((const char*)doc["evento"]);
    if (doc["clientes_acumulados"].is<int>()) {
      Serial.print("   Total clientes: ");
      Serial.println((int)doc["clientes_acumulados"]);
    }
  }
  else if (strcmp(subtopico, "alertas") == 0) {
    const char* prio = doc["prioridad"];
    Serial.print("ALERTA [");
    Serial.print(prio);
    Serial.print("] ");
    Serial.println((const char*)doc["tipo"]);
    Serial.print("   ");
    Serial.println((const char*)doc["mensaje"]);
  }

  if (!ok) Serial.println("   FALLO AL PUBLICAR EN MQTT");
  Serial.println("---------------------------------------------");
  Serial.println();
}

// ------------------------------------------------------------------
// Registra una alerta. NO enciende el LED directamente.
// ------------------------------------------------------------------
void alerta(int slot, const char* tipo, const char* mensaje, const char* prioridad) {
  if (ultimaAlerta[slot] == tipo) return;   // ya notificada
  ultimaAlerta[slot] = tipo;
  JsonDocument d;
  d["nodo"] = "esp32-panaderia-01";
  d["tipo"] = tipo;
  d["mensaje"] = mensaje;
  d["prioridad"] = prioridad;
  d["uptime_s"] = millis() / 1000;
  publicar("alertas", d);
  tone(PIN_BUZZER, 1000, 300);   // solo buzzer
}

void limpiarAlerta(int slot) { ultimaAlerta[slot] = ""; }

// ------------------------------------------------------------------
// LED: se enciende si CUALQUIERA de estas condiciones es verdadera:
//   1) Peso < PESO_ALERTA_G
//   2) Temp vitrina > TEMP_VITRINA_MAX
//   3) Humedad vitrina > HUM_VITRINA_MAX
//   4) Puerta abierta
// ------------------------------------------------------------------
void actualizarLed() {
  bool pesoBajo          = (pesoActual < PESO_ALERTA_G);
  bool tempAlta          = (!isnan(tempActual) && tempActual > TEMP_VITRINA_MAX);
  bool humAlta           = (!isnan(humActual) && humActual > HUM_VITRINA_MAX);
  bool puertaAbiertaFlag = puertaAbierta;

  bool encender = pesoBajo || tempAlta || humAlta || puertaAbiertaFlag;

  digitalWrite(PIN_LED, encender ? HIGH : LOW);
}

void leerEventos() {
  // Puerta
  bool abierta = (digitalRead(PIN_PUERTA) == LOW);
  if (abierta != puertaAbierta) {
    puertaAbierta = abierta;
    alertaPuertaEnviada = false;
    if (abierta) tPuertaAbierta = millis();
    JsonDocument d;
    d["nodo"] = "esp32-panaderia-01";
    d["evento"] = abierta ? "puerta_abierta" : "puerta_cerrada";
    d["uptime_s"] = millis() / 1000;
    publicar("eventos", d);
  }
  if (puertaAbierta && !alertaPuertaEnviada && millis() - tPuertaAbierta > PUERTA_MAX_MS) {
    alertaPuertaEnviada = true;
    ultimaAlerta[2] = "";
    alerta(2, "puerta_abierta_prolongada", "Puerta de almacen abierta por mas de 10 s", "MEDIA");
  }
  if (!puertaAbierta) limpiarAlerta(2);

  // Cliente (PIR): cuenta si esta activo y pasaron al menos PIR_DEBOUNCE_MS
  // desde el ultimo conteo. No exige flanco de subida para facilitar la demo.
  bool pir = digitalRead(PIN_PIR) == HIGH;
  if (pir && millis() - tUltimoCliente > PIR_DEBOUNCE_MS) {
    clientes++;
    tUltimoCliente = millis();
    JsonDocument d;
    d["nodo"] = "esp32-panaderia-01";
    d["evento"] = "cliente_detectado";
    d["clientes_acumulados"] = clientes;
    publicar("eventos", d);
  }

  // Actualizar LED tras cualquier cambio (incluida la puerta)
  actualizarLed();
}

void telemetria() {
  // ----- Lectura del peso simulado con potenciómetro (0 a 5000 g) -----
  int adc = analogRead(PIN_PESO);              // 0..4095
  float peso = PESO_MIN_G + (adc / 4095.0) * (PESO_MAX_G - PESO_MIN_G);
  int unidades = (int)round(peso / PESO_UNITARIO_G);

  float t = dht.readTemperature();
  float h = dht.readHumidity();

  // Guardar valores para el control del LED
  pesoActual = peso;
  tempActual = t;
  humActual  = h;

  JsonDocument d;
  d["nodo"] = "esp32-panaderia-01";
  if (!isnan(t)) d["vitrina_temp_c"] = round(t * 10) / 10.0;
  if (!isnan(h)) d["vitrina_hum_pct"] = round(h * 10) / 10.0;
  d["bandeja_peso_g"] = round(peso);
  d["bandeja_unidades"] = unidades;
  d["puerta_abierta"] = puertaAbierta;
  d["clientes_acumulados"] = clientes;
  d["wifi_rssi"] = WiFi.RSSI();
  d["uptime_s"] = millis() / 1000;
  publicar("telemetria", d);

  // ----- Reglas de alerta -----
  if (!isnan(t) && t > TEMP_VITRINA_MAX) {
    alerta(0, "temp_vitrina_alta", "Temperatura de vitrina sobre el umbral", "ALTA");
  } else limpiarAlerta(0);

  if (!isnan(h) && h > HUM_VITRINA_MAX) {
    alerta(1, "humedad_alta", "Humedad de vitrina sobre el umbral", "MEDIA");
  } else if (ultimaAlerta[1] == "humedad_alta") limpiarAlerta(1);

  // Stock bajo: se alerta una vez, se re-arma cuando el stock se recupera
  if (unidades >= 0 && unidades < STOCK_MIN_UNIDADES) {
    if (!stockEnviado) {
      JsonDocument s;
      s["nodo"] = "esp32-panaderia-01";
      s["tipo"] = "stock_bajo_bandeja";
      s["mensaje"] = "Unidades estimadas bajo el minimo";
      s["prioridad"] = "MEDIA";
      s["unidades"] = unidades;
      publicar("alertas", s);
      stockEnviado = true;
    }
  } else {
    stockEnviado = false;   
  }

  // Refrescar LED según todas las condiciones
  actualizarLed();
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("=============================================");
  Serial.println("  PANADERIA LOS ANDES - GRUPO 03");
  Serial.println("  Sistema IoT de monitoreo");
  Serial.println("  Nodo: esp32-panaderia-01");
  Serial.println("=============================================");
  Serial.println();

  pinMode(PIN_PUERTA, INPUT_PULLUP);
  pinMode(PIN_PIR, INPUT);
  pinMode(PIN_PESO, INPUT);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);

  dht.begin();
  conectarWifi();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(512);

  // Estado inicial del LED
  pesoActual = 0;
  tempActual = NAN;
  humActual  = NAN;
  actualizarLed();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) conectarWifi();
  if (!mqtt.connected()) conectarMqtt();
  mqtt.loop();
  leerEventos();
  if (millis() - tTelemetria >= INTERVALO_TELEMETRIA_MS) {
    tTelemetria = millis();
    telemetria();
  }
}