package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ClienteRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;

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

    @Test
    void registrarVentaDescuentaElStockYNoExponeLaContrasena() throws Exception {
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
                 "detalles": [{"producto_id": {"id": %d}, "cantidad": 3, "subtotal": 1.50}],
                 "total": 1.50}
                """.formatted(cliente.getId(), producto.getId());

        mockMvc.perform(post("/venta").with(user(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(venta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario_id.password").doesNotExist())
                .andExpect(jsonPath("$.total", Matchers.is(1.5)));

        assertEquals(47, productoRepository.findById(producto.getId()).orElseThrow().getCantidad());
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
