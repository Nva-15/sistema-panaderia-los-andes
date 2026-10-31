package com.proyecto.panaderialosandes.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.panaderialosandes.services.IotService;

import jakarta.annotation.PreDestroy;

// se suscribe a los topicos MQTT que publica el ESP32 de la panaderia y guarda los datos en el sistema:
//   <base>/telemetria -> lecturas de sensores
//   <base>/alertas    -> alertas del dispositivo
//   <base>/estado     -> "online" / "offline" (ultima voluntad del dispositivo)
@Component
public class MqttIotListener implements MqttCallbackExtended {

    private static final Logger logger = LoggerFactory.getLogger(MqttIotListener.class);

    private final IotService iotService;
    private final ObjectMapper objectMapper;
    private final boolean habilitado;
    private final String servidor;
    private final String topicoBase;
    private final String usuario;
    private final String clave;
    private volatile MqttClient cliente;
    private volatile boolean detenido;

    public MqttIotListener(IotService iotService, ObjectMapper objectMapper,
            @Value("${app.iot.mqtt.habilitado:true}") boolean habilitado,
            @Value("${app.iot.mqtt.servidor:tcp://broker.hivemq.com:1883}") String servidor,
            @Value("${app.iot.mqtt.topico-base:losandes/utpgrupo03}") String topicoBase,
            @Value("${app.iot.mqtt.usuario:}") String usuario,
            @Value("${app.iot.mqtt.clave:}") String clave) {
        this.iotService = iotService;
        this.objectMapper = objectMapper;
        this.habilitado = habilitado;
        this.servidor = servidor;
        this.topicoBase = topicoBase.endsWith("/") ? topicoBase.substring(0, topicoBase.length() - 1) : topicoBase;
        this.usuario = usuario;
        this.clave = clave;
    }

    // la conexion se hace en segundo plano para no retrasar el arranque si el broker no responde
    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!habilitado) {
            logger.info("MQTT deshabilitado (MQTT_HABILITADO=false)");
            return;
        }
        Thread hilo = new Thread(this::conectarConReintentos, "mqtt-iot");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void conectarConReintentos() {
        long espera = 5_000;
        while (!detenido) {
            try {
                MqttClient nuevo = new MqttClient(servidor, "panaderia-backend-" + UUID.randomUUID().toString().substring(0, 8),
                        new MemoryPersistence());
                nuevo.setCallback(this);
                MqttConnectOptions opciones = new MqttConnectOptions();
                opciones.setAutomaticReconnect(true);
                opciones.setCleanSession(true);
                opciones.setConnectionTimeout(15);
                opciones.setKeepAliveInterval(60);
                if (usuario != null && !usuario.isBlank()) {
                    opciones.setUserName(usuario);
                    opciones.setPassword(clave.toCharArray());
                }
                // se asigna antes de conectar porque connectComplete (que suscribe) se ejecuta durante connect()
                cliente = nuevo;
                nuevo.connect(opciones);
                return;
            } catch (MqttException e) {
                logger.warn("No se pudo conectar al broker MQTT {} ({}); reintento en {} s", servidor, e.getMessage(), espera / 1000);
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                espera = Math.min(espera * 2, 120_000);
            }
        }
    }

    // se llama al conectar y al reconectar: hay que volver a suscribirse porque la sesion es limpia
    @Override
    public void connectComplete(boolean reconexion, String servidorUri) {
        try {
            cliente().subscribe(new String[] { topicoBase + "/telemetria", topicoBase + "/alertas", topicoBase + "/estado" },
                    new int[] { 0, 1, 1 });
            logger.info("MQTT {} a {}; suscrito a {}/telemetria, /alertas y /estado",
                    reconexion ? "reconectado" : "conectado", servidorUri, topicoBase);
        } catch (MqttException e) {
            logger.error("No se pudo suscribir a los topicos MQTT", e);
        }
    }

    @Override
    public void messageArrived(String topico, MqttMessage mensaje) {
        String contenido = new String(mensaje.getPayload(), StandardCharsets.UTF_8);
        try {
            String subtopico = topico.substring(topico.lastIndexOf('/') + 1);
            if (subtopico.equals("estado")) {
                iotService.registrarConexion(contenido);
                return;
            }
            Map<String, Object> datos = objectMapper.readValue(contenido, new TypeReference<Map<String, Object>>() {
            });
            String dispositivo = String.valueOf(datos.getOrDefault("nodo", "esp32"));
            if (subtopico.equals("telemetria")) {
                iotService.registrarTelemetria(dispositivo, datos);
            } else if (subtopico.equals("alertas")) {
                String mensajeAlerta = texto(datos.get("mensaje"));
                if (datos.get("unidades") instanceof Number unidades) {
                    mensajeAlerta += " (" + unidades.intValue() + " unidades)";
                }
                iotService.registrarAlerta(dispositivo, texto(datos.get("tipo")), mensajeAlerta, texto(datos.get("prioridad")));
            }
        } catch (Exception e) {
            // un mensaje mal formado no debe cortar la conexion MQTT
            logger.warn("Mensaje MQTT ignorado en {}: {} ({})", topico, contenido, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable causa) {
        logger.warn("Conexion MQTT perdida: {}. Reconectando...", causa == null ? "" : causa.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // este componente solo recibe mensajes
    }

    public boolean estaConectado() {
        MqttClient actual = cliente;
        return actual != null && actual.isConnected();
    }

    private MqttClient cliente() {
        return cliente;
    }

    @PreDestroy
    public void detener() {
        detenido = true;
        MqttClient actual = cliente;
        if (actual != null) {
            try {
                actual.disconnect();
                actual.close();
            } catch (MqttException e) {
                logger.debug("Error al cerrar MQTT: {}", e.getMessage());
            }
        }
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
