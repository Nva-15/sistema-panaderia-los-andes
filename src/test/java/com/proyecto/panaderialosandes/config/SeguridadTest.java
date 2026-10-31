package com.proyecto.panaderialosandes.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// verifica los permisos por rol definidos en SecurityConfig
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usuarioSinSesionEsEnviadoAlLogin() throws Exception {
        mockMvc.perform(get("/principal/venta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void vendedorPuedeEntrarAVentas() throws Exception {
        mockMvc.perform(get("/principal/venta").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk());
    }

    @Test
    void vendedorNoPuedeAdministrarUsuarios() throws Exception {
        mockMvc.perform(get("/login/dataUsuarios").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/login/eliminar-usuario/1").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/login/guardar").with(user("vende").roles("VENDEDOR"))
                .param("nombre", "x").param("username", "intruso").param("password", "Aa1!x").param("rol", "admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendedorNoPuedeDescargarReportes() throws Exception {
        mockMvc.perform(get("/api/reportes/productos/excel").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reporte-ventas/filtros").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeVerUsuarios() throws Exception {
        mockMvc.perform(get("/login/dataUsuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
