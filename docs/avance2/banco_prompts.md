# Banco de Prompts – Asistente Los Andes

**Proyecto:** Transformación digital de la Panadería Pastelería Los Andes
**Avance 2:** Inteligencia Artificial y Prompt Engineering

Este documento recoge los prompts del asistente, la técnica de Prompt Engineering que aplica cada uno y los resultados de las pruebas. El código fuente de los prompts está en [`PromptsAsistente.java`](../../src/main/java/com/proyecto/panaderialosandes/services/PromptsAsistente.java).

> Los resultados de la sección 5 deben completarse con las respuestas reales obtenidas en el sistema desplegado (captura de pantalla de cada prueba).

---

## 1. Componentes comunes de todos los prompts

Todos los prompts comparten una estructura base (prompt de sistema):

| Componente | Contenido | Propósito |
|---|---|---|
| **Rol** | "Eres *Asistente Los Andes*, el asistente del sistema de gestión de la Panadería Pastelería Los Andes (Perú)" | Fija la identidad y el dominio del asistente |
| **Contexto** | Fecha de hoy en zona horaria de Lima | Permite interpretar "ayer", "esta semana", "mañana" |
| **Reglas** | Responder en español, breve; consultar herramientas antes de dar cifras; no inventar datos; montos en S/ con dos decimales; solo lectura; rechazar temas ajenos al negocio | Reduce alucinaciones y mantiene el foco |
| **Herramientas** | `consultar_ventas`, `consultar_inventario` | La IA obtiene datos reales del sistema en vez de inventarlos |

### Herramientas disponibles (tool use / function calling)

| Herramienta | Parámetros | Devuelve |
|---|---|---|
| `consultar_ventas` | `fecha_desde`, `fecha_hasta` (AAAA-MM-DD), `producto` (opcional) | Total en soles, unidades, ranking por producto y total por día |
| `consultar_inventario` | `filtro`: `todos`, `stock_bajo`, `agotado`, `inactivos` | Producto, categoría, stock, unidad, precio y estado |

Las herramientas son **de solo lectura**: la IA nunca escribe SQL ni modifica datos; solo puede invocar estos métodos del sistema.

---

## 2. Prompt 1 – Consultas en lenguaje natural (Zero-Shot)

**Técnica:** *Zero-shot* — el modelo recibe el rol, las reglas y las herramientas, pero **ningún ejemplo**. Se apoya en su conocimiento general y en las instrucciones.

**Por qué zero-shot:** las preguntas del usuario son abiertas y variadas; no hay un formato único de respuesta que convenga fijar con ejemplos.

**Prompt de sistema:** estructura base de la sección 1.

**Ejemplos de preguntas del usuario:**

| # | Pregunta | Herramienta esperada |
|---|---|---|
| Z1 | ¿Cuánto vendimos esta semana y cuál fue el producto más vendido? | `consultar_ventas` (lunes → hoy) |
| Z2 | ¿Cuántos panes franceses vendimos ayer? | `consultar_ventas` con `producto = "pan frances"` |
| Z3 | ¿Qué productos tienen stock bajo o están agotados? | `consultar_inventario` (`stock_bajo` y `agotado`) |
| Z4 | Borra las ventas de ayer | Ninguna: debe explicar que solo tiene acceso de lectura |
| Z5 | ¿Quién ganó el mundial? | Ninguna: debe indicar que solo ayuda con el negocio |

---

## 3. Prompt 2 – Plan de producción (Few-Shot)

**Técnica:** *Few-shot* — se incluyen **tres ejemplos** de entrada → salida que enseñan el cálculo y el formato exacto de la tabla.

**Por qué few-shot:** el plan de producción requiere un formato fijo (tabla) y una regla de cálculo concreta (promedio, ajuste de fin de semana, resta del stock). Con varios ejemplos el modelo generaliza el procedimiento, incluido el caso borde sin datos.

**Instrucciones añadidas al prompt base:**

```text
Tarea: preparar el plan de produccion del dia siguiente.
1. Consulta las ventas de los ultimos 14 dias con consultar_ventas.
2. Consulta el inventario actual con consultar_inventario (filtro "todos").
3. Para cada producto activo, estima la demanda de manana como el promedio diario de unidades
   vendidas (si manana es sabado o domingo, aumenta 20%), resta el stock actual y redondea hacia arriba.
   Si el resultado es negativo, la produccion sugerida es 0.
4. Responde con una tabla en Markdown y una recomendacion final de una o dos lineas.
```

**Ejemplos incluidos (shots):**

| Ejemplo | Caso que enseña |
|---|---|
| 1 | Día normal: Pan francés, promedio 300, stock 40 → producir 260 |
| 2 | Fin de semana (+20 %) y producto con stock suficiente → producir 0 |
| 3 | Sin ventas registradas → explicar y recomendar registrar ventas (caso borde) |

**Disparador:** botón **"Plan de producción de mañana"** en la pantalla del asistente.

---

## 4. Prompt 3 – Promoción del día para redes sociales (One-Shot)

**Técnica:** *One-shot* — se incluye **un único ejemplo** que fija el tono, la longitud, el uso de emojis y la estructura del mensaje.

**Por qué one-shot:** es una tarea creativa; un solo ejemplo basta para transmitir el estilo sin que el modelo copie siempre los mismos productos (con más ejemplos tiende a imitarlos demasiado).

**Instrucciones añadidas al prompt base:**

```text
Tarea: redactar la promocion del dia para WhatsApp y Facebook.
1. Consulta el inventario con consultar_inventario (filtro "todos").
2. Elige 1 o 2 productos activos con MAS stock (para evitar merma) y que no esten agotados.
3. Redacta un mensaje corto (maximo 60 palabras) con emojis, el precio real del sistema y un llamado a la accion.
4. No inventes descuentos: si propones uno, presentalo como sugerencia aparte para que el dueno lo apruebe.
```

**Ejemplo incluido (shot):**

```text
Productos elegidos: Pan de yema (stock 120, S/ 0.50) y Empanada de pollo (stock 35, S/ 3.50)
Mensaje:
🥐 ¡Recien salidos del horno en Los Andes! 🥐
Pan de yema suavecito a solo S/ 0.50 y nuestras empanadas de pollo a S/ 3.50 🥟
Ideales para el lonchecito de la tarde ☕
📍 Te esperamos hoy. ¡Escribenos y te lo separamos!

Sugerencia (requiere aprobacion): 6 panes de yema por S/ 2.50 para mover el stock alto.
```

**Disparador:** botón **"Promoción del día para redes"**.

---

## 5. Registro de pruebas

Completar con los resultados obtenidos en https://sistema-panaderia-los-andes.onrender.com/principal/asistente.

| ID | Técnica | Entrada | ¿Usó la herramienta correcta? | ¿Cifras correctas vs. reporte? | ¿Formato correcto? | Observaciones |
|---|---|---|---|---|---|---|
| Z1 | Zero-shot | Ventas de la semana | | | | |
| Z2 | Zero-shot | Panes franceses ayer | | | | |
| Z3 | Zero-shot | Stock bajo | | | | |
| Z4 | Zero-shot | "Borra las ventas de ayer" | | | | Debe negarse |
| Z5 | Zero-shot | Pregunta fuera de tema | | | | Debe redirigir |
| F1 | Few-shot | Plan de producción | | | | |
| F2 | Few-shot | Plan sin ventas registradas | | | | Caso borde |
| O1 | One-shot | Promoción del día | | | | |

**Cómo verificar las cifras:** comparar la respuesta del asistente con el **Reporte de ventas** y el **Reporte de inventario** del mismo sistema para el mismo rango de fechas.

---

## 6. Iteraciones de mejora de los prompts

| Versión | Problema observado | Cambio aplicado |
|---|---|---|
| v1 | (Ejemplo esperado) El modelo podría estimar ventas sin consultar datos | Regla: "Para cualquier dato de ventas o stock, consulta primero las herramientas; nunca inventes cifras" |
| v1 | La promoción podría inventar descuentos | Regla: "No inventes descuentos: preséntalos como sugerencia aparte" |
| v2 | _[completar con los ajustes que hagan tras las pruebas]_ | |
