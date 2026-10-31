package com.proyecto.panaderialosandes.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.models.DetalleVentas;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.models.Ventas;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ClienteRepository;
import com.proyecto.panaderialosandes.repositorios.DetalleVentaRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;
import com.proyecto.panaderialosandes.repositorios.VentaRepository;

// prueba las herramientas que usa el asistente de IA contra la base de datos de pruebas (sin llamar a la IA)
@SpringBootTest
class HerramientasAsistenteTest {

    @Autowired
    private HerramientasAsistente herramientas;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    private Productos pan;

    @BeforeEach
    void datos() {
        detalleVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        productoRepository.deleteAll();

        pan = crearProducto("Pan de piso asistente", 120, 0.40);
        crearProducto("Torta helada asistente", 5, 25.00);
        crearProducto("Pionono asistente", 0, 18.00);

        Clientes cliente = new Clientes();
        cliente.setNombre("Cliente IA");
        cliente.setDni("87654321");
        cliente = clienteRepository.save(cliente);

        Ventas venta = new Ventas();
        venta.setCliente_id(cliente);
        venta.setUsuario_id(usuarioRepository.findByUsername("admin").orElseThrow());
        venta.setFecha(new Date());
        venta.setTotal(4.00);
        venta = ventaRepository.save(venta);

        DetalleVentas detalle = new DetalleVentas();
        detalle.setVenta_id(venta);
        detalle.setProducto_id(pan);
        detalle.setCantidad(10);
        detalle.setSubtotal(4.00);
        detalleVentaRepository.save(detalle);
    }

    private Productos crearProducto(String nombre, int stock, double precio) {
        Productos p = new Productos();
        p.setNombre(nombre);
        p.setCategoria_id(categoriaRepository.findAll().get(0));
        p.setCantidad(stock);
        p.setPrecio(precio);
        p.setUnidad("unidad");
        return productoRepository.save(p);
    }

    @Test
    void consultarVentasDevuelveTotalesYUnidadesPorProducto() {
        String hoy = LocalDate.now().toString();

        Map<String, Object> resultado = herramientas.consultarVentas(hoy, hoy, null);

        assertEquals(4.0, resultado.get("total_soles"));
        assertEquals(10L, resultado.get("unidades_vendidas"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productos = (List<Map<String, Object>>) resultado.get("por_producto");
        assertEquals("Pan de piso asistente", productos.get(0).get("producto"));
    }

    @Test
    void consultarVentasFiltraPorNombreDeProducto() {
        String hoy = LocalDate.now().toString();

        Map<String, Object> resultado = herramientas.consultarVentas(hoy, hoy, "torta");

        assertEquals(0.0, resultado.get("total_soles"));
    }

    @Test
    void consultarInventarioAplicaLosFiltros() {
        assertEquals(1, herramientas.consultarInventario("stock_bajo").size());
        assertEquals("Torta helada asistente", herramientas.consultarInventario("stock_bajo").get(0).get("producto"));
        assertEquals(1, herramientas.consultarInventario("agotado").size());
        assertEquals(3, herramientas.consultarInventario("todos").size());
    }

    @Test
    void ejecutarRechazaFechasInvalidasYHerramientasDesconocidas() {
        assertThrows(IllegalArgumentException.class,
                () -> herramientas.ejecutar(HerramientasAsistente.CONSULTAR_VENTAS,
                        Map.of("fecha_desde", "ayer", "fecha_hasta", "hoy")));
        assertThrows(IllegalArgumentException.class, () -> herramientas.ejecutar("borrar_todo", Map.of()));
    }

    @Test
    void lasDefinicionesIncluyenAmbasHerramientas() {
        List<String> nombres = herramientas.definiciones().stream().map(t -> t.name().orElse("")).toList();
        assertTrue(nombres.containsAll(List.of("consultar_ventas", "consultar_inventario")));
    }
}
