package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Date;

import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ClienteRepository;
import com.proyecto.panaderialosandes.repositorios.DetalleVentaRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;
import com.proyecto.panaderialosandes.repositorios.VentaRepository;
import com.proyecto.panaderialosandes.services.IotService;

// prueba de integracion: registrar una venta descuenta el stock del producto
@SpringBootTest
@AutoConfigureMockMvc
class VentasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @BeforeEach
    void limpiarVentasDePrueba() {
        detalleVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    @Test
    void vistaDeVentaIncluyeModalDeConfirmacion() throws Exception {
        mockMvc.perform(get("/principal/venta").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Confirmar venta")))
                .andExpect(content().string(Matchers.containsString("confirmarVenta")))
                .andExpect(content().string(Matchers.containsString("avisoVentaModal")))
                .andExpect(content().string(Matchers.containsString("Agregue al menos un producto a la venta.")));
    }

    @Test
    void registrarVentaGuardaCabeceraDetalleYPrecioCalculadoEnServidor() throws Exception {
        Productos producto = new Productos();
        producto.setNombre("Pan de yema");
        producto.setCategoria_id(categoriaRepository.findAll().get(0));
        producto.setCantidad(50);
        producto.setPrecio(0.50);
        producto.setUnidad("unidad");
        producto = productoRepository.save(producto);

        Clientes cliente = new Clientes();
        cliente.setNombre("Cliente Prueba");
        cliente.setDni("12345678");
        cliente = clienteRepository.save(cliente);

        Usuarios vendedor = usuarioRepository.findByUsername("admin").orElseThrow();

        String venta = """
                {"cliente_id": {"id": %d},
                 "detalles": [{"producto_id": {"id": %d}, "cantidad": 3, "subtotal": 999.00}],
                 "total": 999.00}
                """.formatted(cliente.getId(), producto.getId());

        mockMvc.perform(post("/venta").with(user(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(venta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.usuario_id.password").doesNotExist())
                .andExpect(jsonPath("$.total", Matchers.is(1.5)));

        int productoId = producto.getId();
        assertEquals(47, productoRepository.findById(productoId).orElseThrow().getCantidad());
        var detalle = detalleVentaRepository.findAll().stream()
                .filter(item -> item.getProducto_id().getId() == productoId)
                .findFirst().orElseThrow();
        assertEquals(3, detalle.getCantidad());
        assertEquals(1.5, detalle.getSubtotal());

        var ventaRegistrada = detalle.getVenta_id();
        ventaRegistrada.setFecha(Date.from(LocalDate.now(IotService.ZONA).atTime(12, 0)
                .atZone(IotService.ZONA).toInstant()));
        ventaRepository.save(ventaRegistrada);
        String hoyEnPeru = LocalDate.now(IotService.ZONA).toString();

        mockMvc.perform(get("/api/reporte-ventas/ventas")
                .param("fechaDesde", hoyEnPeru)
                .param("fechaHasta", hoyEnPeru)
                .with(user(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0][3]", Matchers.is("Cliente Prueba")))
                .andExpect(jsonPath("$[0][4]", Matchers.is("12345678")))
                .andExpect(jsonPath("$[0][8]", Matchers.is(0.5)))
                .andExpect(jsonPath("$[0][9]", Matchers.is(1.5)));

        Usuarios usuarioVendedor = new Usuarios();
        usuarioVendedor.setNombre("Vendedor de prueba");
        usuarioVendedor.setUsername("vendedor");
        usuarioVendedor.setPassword("x");
        usuarioVendedor.setRol("VENDEDOR");
        usuarioVendedor.setEstado("activo");
        mockMvc.perform(get("/principal/inicio").with(user(usuarioVendedor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Productos vendidos hoy")))
                .andExpect(content().string(Matchers.containsString("Pan de yema")))
                .andExpect(content().string(Matchers.containsString("3 u.")));
    }

    @Test
    void rechazaVentaSinStockYNoGuardaNingunCambio() throws Exception {
        Productos producto = productoRepository.save(nuevoProducto("Pan sin stock de prueba", "activo"));
        producto.setCantidad(2);
        producto = productoRepository.save(producto);

        Clientes cliente = new Clientes();
        cliente.setNombre("Cliente sin stock");
        cliente.setDni("87654321");
        cliente = clienteRepository.save(cliente);

        Usuarios vendedor = usuarioRepository.findByUsername("admin").orElseThrow();
        long ventasAntes = ventaRepository.count();
        long detallesAntes = detalleVentaRepository.count();
        String venta = """
                {"cliente_id": {"id": %d},
                 "detalles": [{"producto_id": {"id": %d}, "cantidad": 3, "subtotal": 0.30}],
                 "total": 0.30}
                """.formatted(cliente.getId(), producto.getId());

        mockMvc.perform(post("/venta").with(user(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(venta))
                .andExpect(status().isConflict());

        assertEquals(ventasAntes, ventaRepository.count());
        assertEquals(detallesAntes, detalleVentaRepository.count());
        assertEquals(2, productoRepository.findById(producto.getId()).orElseThrow().getCantidad());
    }

    @Test
    void registraClienteNuevoDentroDeLaVenta() throws Exception {
        Productos producto = productoRepository.save(nuevoProducto("Pan cliente nuevo", "activo"));
        Usuarios vendedor = usuarioRepository.findByUsername("admin").orElseThrow();
        String venta = """
                {"cliente": {"dni": "11223344", "nombre": "Cliente Nuevo", "telefono": "987654321",
                             "correo": "nuevo@example.com", "direccion": "Calle 1"},
                 "detalles": [{"producto_id": {"id": %d}, "cantidad": 1, "subtotal": 99}],
                 "total": 99}
                """.formatted(producto.getId());

        mockMvc.perform(post("/venta").with(user(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(venta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", Matchers.is(1.0)));

        Clientes clienteGuardado = clienteRepository.findByDni("11223344");
        assertTrue(clienteGuardado != null);
        assertEquals("Cliente Nuevo", clienteGuardado.getNombre());
    }

    @Test
    void laVentaNoOfreceProductosInactivos() throws Exception {
        Productos activo = productoRepository.save(nuevoProducto("Pan activo de prueba", "activo"));
        Productos inactivo = productoRepository.save(nuevoProducto("Pan inactivo de prueba", "inactivo"));

        mockMvc.perform(get("/venta/datosventas").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[?(@.id == " + activo.getId() + ")]").exists())
                .andExpect(jsonPath("$.productos[?(@.id == " + inactivo.getId() + ")]").doesNotExist());
    }

    private Productos nuevoProducto(String nombre, String estado) {
        Productos p = new Productos();
        p.setNombre(nombre);
        p.setCategoria_id(categoriaRepository.findAll().get(0));
        p.setCantidad(20);
        p.setPrecio(1.0);
        p.setUnidad("unidad");
        p.setEstado(estado);
        return p;
    }
}
