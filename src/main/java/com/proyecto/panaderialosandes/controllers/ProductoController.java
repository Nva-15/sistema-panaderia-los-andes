package com.proyecto.panaderialosandes.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.panaderialosandes.models.Categorias;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;
import com.proyecto.panaderialosandes.services.ProductoService;

import jakarta.servlet.http.HttpServletResponse;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;

    private static final Logger logger = LoggerFactory.getLogger(ProductoController.class);

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public ProductoController(ProductoService productoService, CategoriaRepository categoriaRepository,
            ProductoRepository productoRepository) {
        this.productoService = productoService;
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    @GetMapping("/nuevo") // localhost:8081/productos/nuevo
    public String agregarProducto(Model model) {
        model.addAttribute("producto", new Productos());
        ;
        return "vista/agregar_producto";
    }

    @PostMapping("/guardar")
    public String guardarProducto(@RequestParam("categoria_id") int categoriaId,
            @ModelAttribute Productos producto) {
        Categorias categoria = categoriaRepository.findById(categoriaId).orElse(null);
        producto.setCategoria_id(categoria);
        productoRepository.save(producto);
        return "redirect:/principal/productos";
    }

    // metodo para buscar un producto en el menu principal
    @PostMapping("/buscar")
    public String buscarProducto(@RequestParam("busqueda") String busqueda, Model model) {

        logger.info("Busqueda: {}", busqueda);

        List<Productos> productos = productoService.buscarPorNombre(busqueda);
        model.addAttribute("productos", productos);

        // obtener el usuario
        return "vista/lista_producto";

    }

    @RequestMapping(value = "/filtros", method = { RequestMethod.GET, RequestMethod.POST })
    public String filtrosdeReporte(@RequestParam() String filtro, Model model) {

        List<Productos> productos = productoService.obtenerTodosLosProductos();
        if (filtro.equals("inactivo")) {
            List<Productos> productosInactivos = productos.stream()
                    .filter(p -> p.getEstado().equals("inactivo")) // Filtrar productos agotados
                    .toList(); // Convertir a lista
            model.addAttribute("productos", productosInactivos);
            model.addAttribute("filtro", filtro);
        } else if (filtro.equals("todos")) {
            model.addAttribute("productos", productos);
            model.addAttribute("filtro", filtro);

        } else if (filtro.equals("disponibles")) {
            List<Productos> activosP = productos.stream()
                    .filter(p -> p.getEstado().equals("activo")) // Filtrar productos agotados
                    .toList(); // Convertir a lista
            model.addAttribute("productos", activosP);
            model.addAttribute("filtro", filtro);

        } else if (filtro.equals("agotado")) {

            List<Productos> productosAgotados = productos.stream()
                    .filter(p -> p.getCantidad() == 0) // Filtrar productos agotados
                    .toList(); // Convertir a lista
            model.addAttribute("productos", productosAgotados);
            model.addAttribute("filtro", filtro);

        } else if (filtro.equals("stock_bajo")) {
            List<Productos> productosAgotados = productos.stream()
                    .filter(p -> p.getCantidad() <= 10 && p.getCantidad() > 0) // Filtrar productos agotados
                    .toList(); // Convertir a lista
            model.addAttribute("productos", productosAgotados);
            model.addAttribute("filtro", filtro);
        }

        LocalDate fechaActual = LocalDate.now();
        model.addAttribute("fechaActualizacion", fechaActual);

        return "vista/reporte_inventario";

    }

    @PostMapping("/cambiar_estado")
    public String cambiarEstado(@RequestParam int id, @RequestParam String nuevoEstado,
            RedirectAttributes redirectAttributes, @RequestParam String filtro) {

        productoService.cambiarEstadoUsuario(id, nuevoEstado);

        redirectAttributes.addAttribute("filtro", filtro);

        return "redirect:/productos/filtros"; // Redirigir a la lista de usuarios

    }

    @GetMapping("/dataProductos")
    @ResponseBody
    public ResponseEntity<List<Productos>> enviarData() {
        try {
            List<Productos> productos = productoService.obtenerTodosLosProductos();
            return ResponseEntity.ok(productos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/actualizar/{id}")
    @ResponseBody
    public ResponseEntity<String> actualizarProducto(
            @PathVariable int id, 
            @RequestBody Productos producto) {
        try {
            Optional<Productos> producOptional = productoService.obtenerProductoPorId(id);
             Productos productoNew = new Productos() ;
            if (producOptional.isPresent()) {
                productoNew= producOptional.get();
                productoNew.setNombre(producto.getNombre());
                productoNew.setCantidad(producto.getCantidad());
                productoService.actualizarProducto(productoNew);
            }
                
                return ResponseEntity.ok("Usuario actualizado");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }


    @PostMapping("/exportar-pdf")
    public void exportarPDF(@RequestBody List<Map<String, String>> datos, HttpServletResponse response) throws Exception {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=reporte_inventario.pdf");

        Document document = new Document();
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        document.add(new Paragraph("Reporte de Inventario"));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(7); // 7 columnas
        table.addCell("N°");
        table.addCell("Producto");
        table.addCell("Categoría");
        table.addCell("Stock");
        table.addCell("Condición");
        table.addCell("Estado");
        table.addCell("F.Consulta");

        for (Map<String, String> fila : datos) {
            table.addCell(fila.get("numero"));
            table.addCell(fila.get("producto"));
            table.addCell(fila.get("categoria"));
            table.addCell(fila.get("stock"));
            table.addCell(fila.get("condicion"));
            table.addCell(fila.get("estado"));
            table.addCell(fila.get("fecha"));
        }

        document.add(table);
        document.close();
    }
}