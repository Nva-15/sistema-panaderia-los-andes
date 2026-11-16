package com.proyecto.panaderialosandes.controllers;

import com.proyecto.panaderialosandes.services.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/reporte-ventas")
public class ReporteVentaController {

    private final ReporteVentaService reporteVentaService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;

  
    public ReporteVentaController(ReporteVentaService reporteVentaService,
                                CategoriaService categoriaService,
                                UsuarioService usuarioService) {
        this.reporteVentaService = reporteVentaService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/ventas")
    public ResponseEntity<?> obtenerDatosVentas(
            @RequestParam String fechaDesde,
            @RequestParam String fechaHasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long usuarioId) {
        
        try {
            List<Object[]> ventas = reporteVentaService.obtenerDatosVentas(
                fechaDesde, fechaHasta, categoriaId, usuarioId, null);
            return ResponseEntity.ok(ventas);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener datos de ventas"));
        }
    }

    @GetMapping("/filtros")
    public ResponseEntity<?> obtenerOpcionesFiltros() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("categorias", categoriaService.listarTodas());
            response.put("usuarios", usuarioService.listarVendedoresYAdmin());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al cargar opciones de filtro"));
        }
    }

    @GetMapping("/exportar")
    public ResponseEntity<?> exportarReporteCSV(
            @RequestParam String fechaDesde,
            @RequestParam String fechaHasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long usuarioId) {
        
        try {
            List<Object[]> datos = reporteVentaService.obtenerDatosVentas(
                    fechaDesde, fechaHasta, categoriaId, usuarioId, null);
            
            String csvContent = generarContenidoCSV(datos);
            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("reporte_ventas_" + IotService.ahora().toLocalDate() + ".csv")
                    .build());
            
            return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al generar el reporte CSV"));
        }
    }

    private String generarContenidoCSV(List<Object[]> datos) {
        StringBuilder sb = new StringBuilder();
        sb.append("N.º venta,Fecha,Vendedor,Cliente,DNI,Producto,Categoría,Cantidad,Precio Unitario,Subtotal\n");
        
        for (Object[] fila : datos) {
            sb.append(String.format("%d,", fila[0])); // Venta
            sb.append(String.format("\"%s\",", fila[1])); // Fecha
            sb.append(String.format("\"%s\",", fila[2] != null ? fila[2] : "")); // Usuario
            sb.append(String.format("\"%s\",", fila[3])); // Cliente
            sb.append(String.format("\"%s\",", fila[4])); // DNI
            sb.append(String.format("\"%s\",", fila[5])); // Producto
            sb.append(String.format("\"%s\",", fila[6])); // Categoría
            sb.append(String.format("%d,", fila[7])); // Cantidad
            sb.append(String.format("%.2f,", fila[8])); // Precio
            sb.append(String.format("%.2f\n", fila[9])); // Subtotal
        }
        
        return sb.toString();
    }
}