# Sistema Panadería Pastelería Los Andes

[![CI/CD](https://github.com/Nva-15/sistema-panaderia-los-andes/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Nva-15/sistema-panaderia-los-andes/actions/workflows/ci-cd.yml)

Sistema web de gestión de ventas, inventario, clientes, usuarios y reportes para la Panadería Pastelería Los Andes.
Proyecto integrador del curso **Herramientas de Desarrollo Profesional TIC**.

🌐 **Sistema en la nube:** https://sistema-panaderia-los-andes.onrender.com

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2 (Web, Data JPA, Security) |
| Vistas | Thymeleaf, Bootstrap 5, JavaScript |
| Base de datos | MySQL 8 |
| Reportes | iText (PDF), Apache POI (Excel), CSV |
| DevOps | Git/GitHub, GitHub Actions, Docker, Render |
| Inteligencia Artificial | Groq o Google Gemini (planes gratuitos), *function calling* |
| IoT | ESP32 (Wokwi), MQTT (HiveMQ + Eclipse Paho), Chart.js |
| Automatización | Make (No-Code) + Telegram, GitHub Actions programado, agente de IA |
| AR / VR | `<model-viewer>` (WebAR), A-Frame (WebXR), modelos glTF generados con Python, QR con ZXing |

## Módulos

- **Ventas:** registro de ventas con clientes y descuento automático de stock.
- **Productos e inventario:** alta, búsqueda, edición de stock, activar/desactivar, filtros (agotado, stock bajo).
- **Reportes:** inventario (PDF y Excel) y ventas por fecha, categoría y usuario (CSV).
- **Usuarios:** roles `ADMIN` y `VENDEDOR`, contraseñas cifradas con BCrypt.
- **Asistente IA:** chat que responde preguntas sobre ventas e inventario con datos reales, genera el plan de producción del día siguiente y la promoción del día para redes (Groq o Google Gemini, planes gratuitos).
- **Monitoreo IoT:** dashboard en tiempo real con los sensores del ESP32 (vitrina, bandeja de pan, puerta del almacén y clientes) recibidos por MQTT, gráficos y alertas. Ver [iot/README.md](iot/README.md).
- **Automatizaciones:** reporte de cierre diario, plan de producción con agente de IA, avisos de stock bajo y alertas IoT enviados a Telegram mediante Make. Ver la [guía de configuración](docs/avance4/guia_make_telegram.md).
- **Catálogo en Realidad Aumentada:** página pública (`/catalogo`) para que el cliente escanee un QR y vea las tortas en 3D sobre su mesa, con precio y disponibilidad en vivo.
- **Gemelo digital en VR:** recorrido 3D de la panadería para capacitación, que refleja en vivo los sensores del ESP32.

## Cómo ejecutarlo

### Opción 1: Docker (recomendado, no necesita instalar Java ni MySQL)

```bash
docker compose up --build
```

Abrir http://localhost:8081 e ingresar con usuario `admin` y contraseña `Admin123!` (se crea automáticamente la primera vez).

### Opción 2: GitHub Codespaces (en la nube, desde el navegador)

1. En GitHub: botón **Code → Codespaces → Create codespace on main**.
2. En la terminal del Codespace ejecutar `docker compose up --build`.
3. Abrir el puerto 8081 que aparece en la pestaña **Ports**.

### Opción 3: Java y MySQL instalados en tu PC

1. Copiar `.env.example` como `.env` y completar los datos de tu MySQL.
2. Ejecutar:

```bash
./mvnw spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `DB_URL` | URL JDBC de MySQL (en la nube agregar `sslMode=REQUIRED`) |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de la base de datos |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Administrador inicial; solo se crea si no existen usuarios |
| `PORT` | Puerto del servidor (por defecto 8081; Render lo asigna solo) |
| `GROQ_API_KEY` | API key gratuita de https://console.groq.com para el asistente de IA (proveedor preferido) |
| `GROQ_MODEL` | Opcional. Modelo de Groq (por defecto `openai/gpt-oss-120b`; si no existe, se elige otro disponible automáticamente) |
| `GEMINI_API_KEY` | Alternativa: API key gratuita de Google AI Studio. Sin ninguna key el asistente queda deshabilitado |
| `MQTT_SERVIDOR` | Opcional. Broker MQTT (por defecto `tcp://broker.hivemq.com:1883`) |
| `MQTT_TOPICO_BASE` | Opcional. Tópico base del ESP32 (por defecto `losandes/utpgrupo03`) |
| `MQTT_USUARIO` / `MQTT_CLAVE` | Opcional. Credenciales si el broker las requiere |
| `IOT_API_KEY` | Opcional. Habilita la entrada HTTP alternativa `/api/iot/telemetria` |
| `AUTOMATIZACION_WEBHOOK_URL` | Opcional. URL del webhook de Make que recibe las automatizaciones |
| `AUTOMATIZACION_API_KEY` | Opcional. Clave que usa GitHub Actions para ejecutar las tareas programadas |
| `IA_PROVEEDOR` | Opcional. `groq`, `gemini` o `auto` (por defecto: Groq si tiene key, si no Gemini) |
| `GEMINI_MODEL` | Opcional. Modelo de Gemini (por defecto `gemini-flash-latest`, el Flash más reciente) |
| `GEMINI_THINKING` | Opcional. Nivel de razonamiento: `MINIMAL`, `LOW` (por defecto, más rápido), `MEDIUM` o `HIGH` |

> El archivo `.env` **no se sube a GitHub**. En la nube las credenciales se configuran en el panel de Render y en los *Secrets* de GitHub.

## Pruebas

```bash
./mvnw test
```

Las pruebas usan una base de datos H2 en memoria, por lo que no requieren MySQL.

## CI/CD

El pipeline [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) se ejecuta en cada *push* y *pull request* a `main`:

1. **Compilar y probar** con Maven (Java 21).
2. **Construir la imagen Docker**.
3. **Desplegar en Render**: Render tiene *Auto-Deploy: After CI Checks Pass*, así que publica automáticamente cuando el pipeline termina en verde en `main`.

## Despliegue en la nube

1. Crear una base de datos MySQL gratuita en **Aiven** o **TiDB Cloud**.
2. En **Render**: *New → Web Service*, elegir este repositorio (detecta el `Dockerfile`), plan *Free*, región *Virginia*.
3. Agregar las variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` y `THYMELEAF_CACHE=true`.
4. En *Advanced*: *Health Check Path* `/login` y *Auto-Deploy* **After CI Checks Pass**.

Desde ese momento, cada cambio que llega a `main` y pasa las pruebas se publica automáticamente.

> El plan gratuito de Render apaga el servicio tras un periodo sin uso; la primera visita tarda 1 a 3 minutos en despertarlo.

## Flujo de trabajo del equipo

1. Crear una rama por tarea: `git checkout -b feature/nombre-tarea`.
2. Hacer commits y subir la rama.
3. Abrir un *Pull Request* hacia `main`; el pipeline valida que compile y que las pruebas pasen.
4. Otro integrante revisa y aprueba antes de unir.

## Documentación

- [Avance 1 – Diagnóstico, arquitectura y DevOps](docs/avance1/informe_avance1.md)
- [Avance 2 – Inteligencia Artificial y Prompt Engineering](docs/avance2/informe_avance2.md) · [Banco de prompts](docs/avance2/banco_prompts.md)
- [Avance 3 – Solución IoT](docs/avance3/informe_avance3.md) · [Prototipo ESP32](iot/README.md)
- [Avance 4 – Automatización RPA y No-Code](docs/avance4/informe_avance4.md) · [Guía Make + Telegram](docs/avance4/guia_make_telegram.md)
- [Avance 5 – AR, VR y Metaverso](docs/avance5/informe_avance5.md)
