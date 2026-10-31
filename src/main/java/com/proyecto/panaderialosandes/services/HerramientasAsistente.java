package com.proyecto.panaderialosandes.services;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.google.genai.types.FunctionDeclaration;
import com.proyecto.panaderialosandes.models.Productos;

// herramientas (tool use) que el asistente de IA puede llamar para consultar datos reales del sistema.
// Todas son de solo lectura: la IA nunca modifica datos ni escribe SQL, solo llama a estos metodos.
@Service
public class HerramientasAsistente {

    public static final String CONSULTAR_VENTAS = "consultar_ventas";
    public static final String CONSULTAR_INVENTARIO = "consultar_inventario";

    private final ReporteVentaService reporteVentaService;
    private final ProductoService productoService;

    public HerramientasAsistente(ReporteVentaService reporteVentaService, ProductoService productoService) {
        this.reporteVentaService = reporteVentaService;
        this.productoService = productoService;
    }

    // definicion de las herramientas que se envian al modelo (nombre, descripcion y esquema JSON de entrada)
    public List<FunctionDeclaration> definiciones() {
        FunctionDeclaration ventas = FunctionDeclaration.builder()
                .name(CONSULTAR_VENTAS)
                .description("Consulta las ventas registradas en el sistema entre dos fechas (inclusive). "
                        + "Devuelve el total vendido en soles, las unidades y el monto por producto, y el detalle por dia. "
                        + "Usala para preguntas sobre ventas, productos mas vendidos o para estimar la produccion.")
                .parametersJsonSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "fecha_desde", Map.of("type", "string", "description", "Fecha inicial en formato AAAA-MM-DD"),
                                "fecha_hasta", Map.of("type", "string", "description", "Fecha final en formato AAAA-MM-DD"),
                                "producto", Map.of("type", "string",
                                        "description", "Opcional: parte del nombre de un producto para filtrar, ej. 'pan frances'")),
                        "required", List.of("fecha_desde", "fecha_hasta")))
                .build();

        FunctionDeclaration inventario = FunctionDeclaration.builder()
                .name(CONSULTAR_INVENTARIO)
                .description("Consulta el inventario actual de productos terminados: nombre, categoria, stock, precio y estado. "
                        + "Un producto tiene stock bajo si le quedan 10 unidades o menos.")
                .parametersJsonSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "filtro", Map.of("type", "string",
                                        "enum", List.of("todos", "stock_bajo", "agotado", "inactivos"),
                                        "description", "Que productos devolver")),
                        "required", List.of("filtro")))
                .build();

        return List.of(ventas, inventario);
    }

    // ejecuta la herramienta pedida por el modelo y devuelve el resultado como datos (se serializan a JSON)
    public Object ejecutar(String nombre, Map<String, Object> entrada) {
        return switch (nombre) {
            case CONSULTAR_VENTAS -> consultarVentas(
                    texto(entrada.get("fecha_desde")), texto(entrada.get("fecha_hasta")), texto(entrada.get("producto")));
            case CONSULTAR_INVENTARIO -> consultarInventario(texto(entrada.get("filtro")));
            default -> throw new IllegalArgumentException("Herramienta desconocida: " + nombre);
        };
    }

    public Map<String, Object> consultarVentas(String fechaDesde, String fechaHasta, String producto) {
        validarFecha(fechaDesde);
        validarFecha(fechaHasta);

        // columnas: fecha, usuario, producto, categoria, cantidad, precio, subtotal
        List<Object[]> filas = reporteVentaService.obtenerDatosVentas(fechaDesde, fechaHasta, null, null, null);

        double total = 0;
        long unidades = 0;
        Map<String, double[]> porProducto = new LinkedHashMap<>();
        Map<String, Double> porDia = new TreeMap<>();

        for (Object[] fila : filas) {
            String nombreProducto = String.valueOf(fila[2]);
            if (producto != null && !producto.isBlank()
                    && !nombreProducto.toLowerCase().contains(producto.toLowerCase())) {
                continue;
            }
            long cantidad = ((Number) fila[4]).longValue();
            double subtotal = ((Number) fila[6]).doubleValue();
            String dia = String.valueOf(fila[0]).substring(0, 10);

            total += subtotal;
            unidades += cantidad;
            double[] acumulado = porProducto.computeIfAbsent(nombreProducto, k -> new double[2]);
            acumulado[0] += cantidad;
            acumulado[1] += subtotal;
            porDia.merge(dia, subtotal, Double::sum);
        }

        List<Map<String, Object>> productos = new ArrayList<>();
        porProducto.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> productos.add(Map.of(
                        "producto", e.getKey(),
                        "unidades", (long) e.getValue()[0],
                        "monto_soles", redondear(e.getValue()[1]))));

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("fecha_desde", fechaDesde);
        resultado.put("fecha_hasta", fechaHasta);
        resultado.put("total_soles", redondear(total));
        resultado.put("unidades_vendidas", unidades);
        resultado.put("por_producto", productos);
        porDia.replaceAll((k, v) -> redondear(v));
        resultado.put("total_por_dia", porDia);
        return resultado;
    }

    public List<Map<String, Object>> consultarInventario(String filtro) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Productos p : productoService.obtenerTodosLosProductos()) {
            boolean incluir = switch (filtro == null ? "todos" : filtro) {
                case "stock_bajo" -> p.getCantidad() > 0 && p.getCantidad() <= 10;
                case "agotado" -> p.getCantidad() <= 0;
                case "inactivos" -> "inactivo".equals(p.getEstado());
                default -> true;
            };
            if (incluir) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("producto", p.getNombre());
                fila.put("categoria", p.getCategoria_id() != null ? p.getCategoria_id().getNombre() : "");
                fila.put("stock", p.getCantidad());
                fila.put("unidad", p.getUnidad());
                fila.put("precio_soles", p.getPrecio());
                fila.put("estado", p.getEstado());
                resultado.add(fila);
            }
        }
        return resultado;
    }

    private static void validarFecha(String fecha) {
        try {
            LocalDate.parse(fecha);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Fecha invalida, use el formato AAAA-MM-DD: " + fecha);
        }
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
