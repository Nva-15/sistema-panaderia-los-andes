# Sistema Panadería Pastelería Los Andes

[![CI/CD](https://github.com/Nva-15/sistema-panaderia-los-andes/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Nva-15/sistema-panaderia-los-andes/actions/workflows/ci-cd.yml)

Sistema web de gestión de ventas, inventario, clientes, usuarios y reportes para la Panadería Pastelería Los Andes.
Proyecto integrador del curso **Herramientas de Desarrollo Profesional TIC**.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Security) |
| Vistas | Thymeleaf, Bootstrap 5, JavaScript |
| Base de datos | MySQL 8 |
| Reportes | iText (PDF), Apache POI (Excel), CSV |
| DevOps | Git/GitHub, GitHub Actions, Docker, Render |

## Módulos

- **Ventas:** registro de ventas con clientes y descuento automático de stock.
- **Productos e inventario:** alta, búsqueda, edición de stock, activar/desactivar, filtros (agotado, stock bajo).
- **Reportes:** inventario (PDF y Excel) y ventas por fecha, categoría y usuario (CSV).
- **Usuarios:** roles `ADMIN` y `VENDEDOR`, contraseñas cifradas con BCrypt.

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
3. **Desplegar en Render** (solo en `main` y si todo lo anterior pasó).

## Despliegue en la nube

1. Crear una base de datos MySQL gratuita en **Aiven** o **TiDB Cloud**.
2. En **Render**: *New → Blueprint* y seleccionar este repositorio (usa `render.yaml`). Completar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `ADMIN_PASSWORD`.
3. En Render, en *Settings → Deploy Hook*, copiar la URL y guardarla en GitHub como secreto `RENDER_DEPLOY_HOOK_URL` (*Settings → Secrets and variables → Actions*).

Desde ese momento, cada cambio que llega a `main` y pasa las pruebas se publica automáticamente.

## Flujo de trabajo del equipo

1. Crear una rama por tarea: `git checkout -b feature/nombre-tarea`.
2. Hacer commits y subir la rama.
3. Abrir un *Pull Request* hacia `main`; el pipeline valida que compile y que las pruebas pasen.
4. Otro integrante revisa y aprueba antes de unir.

## Documentación

- [Avance 1 – Diagnóstico, arquitectura y DevOps](docs/avance1/informe_avance1.md)
