package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void administradorPuedeActualizarTodosLosDatosDelProducto() throws Exception {
        Productos producto = nuevoProducto();

        mockMvc.perform(post("/productos/actualizar/{id}", producto.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Pan actualizado",
                          "cantidad": 24,
                          "precio": 3.5,
                          "unidad": "docena",
                          "categoriaId": 2
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().string("Producto actualizado"));

        Productos actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals("Pan actualizado", actualizado.getNombre());
        assertEquals(24, actualizado.getCantidad());
        assertEquals(3.5, actualizado.getPrecio());
        assertEquals("docena", actualizado.getUnidad());
        assertEquals(2, actualizado.getCategoria_id().getId());
    }

    @Test
    void vendedorNoPuedeActualizarProductos() throws Exception {
        Productos producto = nuevoProducto();

        mockMvc.perform(post("/productos/actualizar/{id}", producto.getId())
                .with(user("vende").roles("VENDEDOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pantallaProductosMuestraAccionDeEdicionSoloAlAdministrador() throws Exception {
        nuevoProducto();

        mockMvc.perform(get("/principal/productos").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Editar producto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"modalEditarProducto\"")));

        mockMvc.perform(get("/principal/productos").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Editar producto"))));
    }

    @Test
    void inventarioCargaElEditorCompletoConCategorias() throws Exception {
        mockMvc.perform(get("/productos/filtros")
                .param("filtro", "todos")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"modalEditarProducto\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("categoriaEditar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pan")));
    }

    private Productos nuevoProducto() {
        Productos producto = new Productos();
        producto.setNombre("Producto para editar");
        producto.setCategoria_id(categoriaRepository.findAll().get(0));
        producto.setCantidad(8);
        producto.setPrecio(2.0);
        producto.setUnidad("unidad");
        return productoRepository.save(producto);
    }
}
