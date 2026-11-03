# Guía: Make con Green API (WhatsApp) y Gmail

Tiempo estimado: 25 minutos. Se necesita una cuenta de Make, una instancia de Green API conectada a WhatsApp y una cuenta de Gmail.

## 1. Preparar Green API

1. Iniciar sesión en Green API y crear una instancia, si todavía no se tiene una.
2. Conectar WhatsApp siguiendo las instrucciones de Green API (normalmente, escanear el código QR desde WhatsApp).
3. Confirmar que la instancia aparezca conectada y copiar el **ID de instancia** y el **token de instancia**. Son credenciales privadas: no publicarlas ni incluirlas en capturas.
4. Anotar el número de WhatsApp que recibirá los avisos, con código de país, solo con dígitos y sin `+`, espacios ni guiones. Green API usa el formato `CODIGOPAISNUMERO@c.us` como `chatId`.

## 2. Crear el escenario en Make

1. Crear un escenario en **https://www.make.com**.
2. Agregar como primer módulo **Webhooks → Custom webhook** → **Add** → nombre `panaderia-los-andes` → **Save**.
3. Copiar la URL que Make genere para el webhook.
4. Dejar el webhook escuchando datos de prueba. En el sistema, ir a **Reportes → Automatizaciones → Enviar prueba**. Make debe detectar los campos `evento`, `titulo`, `mensaje`, `fecha` y `datos`.

## 3. Configurar el webhook en Render

En Render → servicio de la aplicación → **Environment**, agregar:

| Variable | Valor |
|---|---|
| `AUTOMATIZACION_WEBHOOK_URL` | URL del webhook de Make |
| `AUTOMATIZACION_API_KEY` | Clave larga y privada, por ejemplo `losandes-auto-2026-xyz` |

Guardar los cambios y esperar a que el servicio vuelva a estar **Live**.

## 4. Enviar los avisos por WhatsApp y Gmail

1. Después del webhook, agregar **Flow Control → Router** para crear dos rutas paralelas.
2. En la primera ruta, agregar **HTTP → Make a request** para enviar el mensaje con Green API:
   - **Method:** `POST`.
   - **URL:** `https://api.green-api.com/waInstance<ID_INSTANCIA>/sendMessage/<TOKEN_INSTANCIA>`; reemplazar los marcadores por las credenciales de Green API.
   - **Body type:** `application/json`.
   - En el cuerpo, enviar `chatId` con el número del destinatario en formato `CODIGOPAISNUMERO@c.us` y `message` con el campo **`mensaje`** recibido del webhook. Si se usa el cuerpo JSON en modo texto, insertar el campo desde el mapeador de Make para conservar correctamente los saltos de línea y caracteres especiales.
3. En la segunda ruta, agregar **Gmail → Send an Email**:
   - Crear la conexión e iniciar sesión con la cuenta de Gmail que enviará los correos.
   - **To:** correo electrónico del destinatario.
   - **Subject:** mapear el campo **`titulo`** del webhook.
   - **Content/Body:** mapear el campo **`mensaje`**.
4. Guardar y activar el escenario con el interruptor **ON**. Configurar el procesamiento como **Immediately**.

El Router enviará cada evento por ambas rutas. Si se quiere enviar ciertos eventos solo a uno de los destinos, agregar un filtro en la ruta correspondiente usando el campo `evento`.

## 5. Probar los envíos

Desde **Reportes → Automatizaciones**, seleccionar **Enviar prueba**. Confirmar que:

- WhatsApp recibe el mensaje y el módulo HTTP de Make termina correctamente.
- Gmail recibe el correo y el módulo Gmail de Make termina correctamente.

Si WhatsApp falla, revisar que la instancia esté conectada, que el ID y token sean correctos y que `chatId` incluya el código de país. Si Gmail falla, revisar la conexión autorizada y la dirección del destinatario. Make muestra el resultado y el error de cada módulo en el historial de ejecuciones.

## 6. Programar las tareas diarias (GitHub Actions)

En GitHub → repositorio → **Settings → Secrets and variables → Actions**:

- Crear el repository secret `AUTOMATIZACION_API_KEY` con la misma clave configurada en Render.

El flujo [`.github/workflows/automatizaciones.yml`](../../.github/workflows/automatizaciones.yml) se ejecuta:

| Hora (Lima) | Automatización |
|---|---|
| 7:00 p. m. | Agente IA: plan de producción |
| 9:00 p. m. | Reporte de cierre diario |

Para probarlo sin esperar: GitHub → **Actions → Automatizaciones → Run workflow** → elegir la automatización.

> GitHub puede demorar algunos minutos en iniciar las tareas programadas y puede desactivarlas si el repositorio pasa 60 días sin actividad.

## Formato que envía el sistema

```json
{
  "evento": "cierre_diario",
  "titulo": "Cierre del día 2026-10-30",
  "mensaje": "Cierre del día 30/10/2026 – Panadería Los Andes\n\nTotal vendido: S/ 245.50\n...",
  "fecha": "2026-10-30T21:00:05",
  "datos": {
    "fecha": "2026-10-30",
    "total_soles": 245.5,
    "unidades_vendidas": 380,
    "productos_stock_bajo": 2,
    "productos_agotados": 1,
    "alertas_iot": 3
  }
}
```

Valores de `evento`: `cierre_diario`, `plan_produccion`, `stock_bajo`, `alerta_iot`.

## Referencias

- Make: documentación de [webhooks](https://www.make.com/en/help/tools/webhooks) y del módulo [HTTP](https://www.make.com/en/help/apps/http).
- Green API: [documentación de la API](https://green-api.com/en/docs/).
- Google: [conectar Gmail a Make](https://www.make.com/en/help/app/gmail).
