# Guía: escenario en Make + Telegram (No-Code)

Tiempo estimado: 20 minutos. Todo con planes gratuitos.

## 1. Crear el bot de Telegram

1. En Telegram, abrir **@BotFather** y enviar `/newbot`.
2. Nombre: `Panadería Los Andes`; usuario: por ejemplo `losandes_alertas_bot` (debe terminar en `bot`).
3. BotFather entrega un **token** (`123456789:ABC...`). Guardarlo; **no compartirlo**.
4. Abrir el bot recién creado y enviarle cualquier mensaje (por ejemplo `hola`), para que pueda escribirte.
5. (Opcional, para un grupo del equipo) Crear un grupo, agregar el bot y escribir un mensaje en el grupo.

## 2. Crear el escenario en Make

1. Crear una cuenta gratuita en **https://www.make.com** → **Create a new scenario**.
2. Primer módulo: **Webhooks → Custom webhook** → **Add** → nombre `panaderia-los-andes` → **Save**.
3. Make muestra una URL como `https://hook.us2.make.com/xxxxxxxx`. **Copiarla.**
4. Hacer clic en **Re-determine data structure** (o dejar el módulo "escuchando").
5. En el sistema: **Reportes → Automatizaciones → Enviar prueba** en cualquier tarjeta (después de configurar la URL en Render, paso 3). Make detecta la estructura: `evento`, `titulo`, `mensaje`, `fecha`, `datos`.

## 3. Configurar la URL en Render

En Render → tu servicio → **Environment** → agregar:

| Variable | Valor |
|---|---|
| `AUTOMATIZACION_WEBHOOK_URL` | la URL del webhook de Make |
| `AUTOMATIZACION_API_KEY` | una clave inventada y larga, por ejemplo `losandes-auto-2026-xyz` |

Guardar y esperar a que el servicio esté **Live**.

## 4. Enviar los mensajes a Telegram

1. En Make, después del webhook, agregar **Telegram Bot → Send a Text Message or a Reply**.
2. **Connection → Add**: pegar el token de BotFather.
3. **Chat ID**: elegir el chat (Make lo lista si ya le escribiste al bot) o escribir tu ID numérico (se obtiene enviando un mensaje a **@userinfobot**).
4. **Text**: hacer clic y elegir el campo **`mensaje`** del webhook.
5. **Save** y activar el escenario con el interruptor **ON** (abajo a la izquierda), con programación **Immediately**.

Probar desde el sistema con **Enviar prueba**: el mensaje debe llegar a Telegram en segundos.

## 5. (Opcional) Router: guardar el cierre diario en Google Sheets

Para tener un historial de ventas diario en una hoja de cálculo:

1. Entre el webhook y Telegram, agregar **Flow Control → Router**.
2. **Ruta 1** → Telegram (todos los eventos).
3. **Ruta 2** → **Google Sheets → Add a Row**, con filtro: `evento` *Equal to* `cierre_diario`.
   - Crear una hoja con columnas: `Fecha | Total vendido | Unidades | Stock bajo | Agotados | Alertas IoT`.
   - Mapear: `datos.fecha`, `datos.total_soles`, `datos.unidades_vendidas`, `datos.productos_stock_bajo`, `datos.productos_agotados`, `datos.alertas_iot`.

## 6. Programar las tareas diarias (GitHub Actions)

En GitHub → repositorio → **Settings → Secrets and variables → Actions**:

- **New repository secret**: `AUTOMATIZACION_API_KEY` = la misma clave del paso 3.

El flujo [`.github/workflows/automatizaciones.yml`](../../.github/workflows/automatizaciones.yml) se ejecuta:

| Hora (Lima) | Automatización |
|---|---|
| 7:00 p. m. | Agente IA: plan de producción |
| 9:00 p. m. | Reporte de cierre diario |

Para probarlo sin esperar: GitHub → **Actions → Automatizaciones → Run workflow** → elegir la automatización.

> GitHub puede demorar algunos minutos en iniciar las tareas programadas, y las desactiva si el repositorio pasa 60 días sin cambios.

## Formato que envía el sistema

```json
{
  "evento": "cierre_diario",
  "titulo": "Cierre del día 2026-10-30",
  "mensaje": "🥖 Cierre del día 30/10/2026 – Panadería Los Andes\n\n💰 Total vendido: S/ 245.50\n...",
  "fecha": "2026-10-30T21:00:05",
  "datos": { "fecha": "2026-10-30", "total_soles": 245.5, "unidades_vendidas": 380, "productos_stock_bajo": 2,
             "productos_agotados": 1, "alertas_iot": 3 }
}
```

Valores de `evento`: `cierre_diario`, `plan_produccion`, `stock_bajo`, `alerta_iot`.
