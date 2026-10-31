# Prototipo IoT – Panadería Los Andes (Grupo 03)

Nodo **ESP32** simulado en Wokwi: https://wokwi.com/projects/477690828673637377

Los archivos de [`wokwi/`](wokwi/) son una copia del prototipo (`sketch.ino`, `diagram.json`, `libraries.txt`).

## Sensores y actuadores

| Componente | Pin | Variable | Umbral / regla |
|---|---|---|---|
| DHT22 | D15 | `vitrina_temp_c`, `vitrina_hum_pct` | Alerta si temperatura > 25 °C o humedad > 70 % |
| Potenciómetro (simula celda de carga) | D34 (ADC) | `bandeja_peso_g` (0–5000 g) y `bandeja_unidades` (60 g por pan) | LED si peso < 1500 g; alerta si < 10 panes |
| Interruptor deslizante | D14 | `puerta_abierta` (puerta del almacén) | Alerta si está abierta más de 10 s |
| Sensor PIR | D13 | `clientes_acumulados` | Cuenta un cliente cada 3 s de movimiento |
| LED rojo | D2 | — | Se enciende si hay alguna condición anormal |
| Buzzer | D4 | — | Suena al emitir una alerta |

## Comunicación (MQTT)

- Broker: `broker.hivemq.com:1883` (público)
- Tópico base: `losandes/utpgrupo03`

| Tópico | Contenido | Uso en el sistema |
|---|---|---|
| `losandes/utpgrupo03/telemetria` | JSON cada 5 s con todas las variables | Lecturas del dashboard (se guardan cada 30 s) |
| `losandes/utpgrupo03/alertas` | `{"tipo", "mensaje", "prioridad"}` | Tabla de alertas del dashboard |
| `losandes/utpgrupo03/eventos` | Puerta abierta/cerrada, cliente detectado | Solo en el monitor serie (el sistema no los guarda) |
| `losandes/utpgrupo03/estado` | `online` / `offline` (última voluntad, retenido) | Estado "ESP32 en línea / desconectado" |

Ejemplo de telemetría:

```json
{"nodo":"esp32-panaderia-01","vitrina_temp_c":23.5,"vitrina_hum_pct":61.0,"bandeja_peso_g":3200,
 "bandeja_unidades":53,"puerta_abierta":false,"clientes_acumulados":4,"wifi_rssi":-58,"uptime_s":120}
```

## Cómo ver los datos en el sistema

1. Iniciar la simulación en Wokwi (botón ▶).
2. Abrir el sistema en la nube → menú **Monitoreo IoT**. El indicador debe mostrar **MQTT conectado** y **ESP32 en línea**.
3. Mover el potenciómetro, cambiar la temperatura del DHT22, abrir la puerta (interruptor) o activar el PIR: las tarjetas y gráficos se actualizan cada 10 segundos.

> El plan gratuito de Render apaga el servidor tras un rato sin visitas, y mientras está apagado no recibe los mensajes MQTT. Al abrir el dashboard el servidor despierta (1–3 minutos) y vuelve a suscribirse; mientras el dashboard está abierto, el servidor se mantiene encendido.

## Entrada HTTP alternativa

Si no se puede usar MQTT, el mismo JSON se puede enviar por HTTP (requiere la variable `IOT_API_KEY` en Render):

```bash
curl -X POST https://sistema-panaderia-los-andes.onrender.com/api/iot/telemetria \
  -H "Content-Type: application/json" -H "X-API-KEY: <clave>" \
  -d '{"nodo":"esp32-panaderia-01","vitrina_temp_c":23.5,"vitrina_hum_pct":61,"bandeja_peso_g":3200,"bandeja_unidades":53,"puerta_abierta":false,"clientes_acumulados":4}'
```
