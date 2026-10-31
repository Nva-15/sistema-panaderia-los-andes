package com.proyecto.panaderialosandes.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.panaderialosandes.models.EventoAutomatizacion;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.EventoAutomatizacionRepository;

// pruebas del rediseno: cada rol ve solo lo que le corresponde y las pantallas nuevas responden
@SpringBootTest
@AutoConfigureMockMvc
class VistasPorRolTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoAutomatizacionRepository eventoRepository;

    @BeforeEach
    void limpiar() {
        eventoRepository.deleteAll();
    }

    private static Usuarios usuario(String username, String rol) {
        Usuarios u = new Usuarios();
        u.setId(99);
        u.setNombre("Prueba " + rol);
        u.setUsername(username);
        u.setPassword("x");
        u.setRol(rol);
        u.setEstado("activo");
        return u;
    }

    @Test
    void administradorVeMenuCompletoYMontosEnElInicio() throws Exception {
        mockMvc.perform(get("/principal/inicio").with(user(usuario("jefa", "admin"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ventas de hoy")))
                .andExpect(content().string(containsString("Ventas de los últimos 7 días")))
                .andExpect(content().string(containsString("/principal/listar_usuarios")))
                .andExpect(content().string(containsString("/principal/automatizaciones")))
                .andExpect(content().string(containsString("/principal/reporte_venta")));
    }

    @Test
    void vendedorNoVeMontosNiOpcionesDeAdministracion() throws Exception {
        mockMvc.perform(get("/principal/inicio").with(user(usuario("vende", "vendedor"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unidades vendidas hoy")))
                .andExpect(content().string(containsString("/principal/venta")))
                .andExpect(content().string(not(containsString("Ventas de hoy"))))
                .andExpect(content().string(not(containsString("/principal/listar_usuarios"))))
                .andExpect(content().string(not(containsString("/principal/automatizaciones"))))
                .andExpect(content().string(not(containsString("/principal/reporte_venta"))))
                .andExpect(content().string(not(containsString("/api/reportes/productos/excel"))));
    }

    @Test
    void vendedorVeProductosSinBotonDeNuevo() throws Exception {
        mockMvc.perform(get("/principal/productos").with(user(usuario("vende", "vendedor"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"busqueda\"")))
                .andExpect(content().string(not(containsString("href=\"/productos/nuevo\""))));
    }

    @Test
    void historialSinParametrosMuestraLasUltimasCinco() throws Exception {
        for (int i = 0; i < 7; i++) {
            guardarEvento(i % 2 == 0 ? EventoAutomatizacion.ENVIADO : EventoAutomatizacion.ERROR, "evento-" + i);
        }
        mockMvc.perform(get("/principal/automatizaciones").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("evento-6")))
                .andExpect(content().string(containsString("evento-2")))
                .andExpect(content().string(not(containsString("evento-1"))))
                .andExpect(content().string(not(containsString("evento-0"))));
    }

    @Test
    void historialFiltraPorEstadoYPagina() throws Exception {
        for (int i = 0; i < 7; i++) {
            guardarEvento(i % 2 == 0 ? EventoAutomatizacion.ENVIADO : EventoAutomatizacion.ERROR, "evento-" + i);
        }
        mockMvc.perform(get("/principal/automatizaciones").with(user("admin").roles("ADMIN"))
                .param("estado", "ERROR").param("periodo", "todo").param("tamano", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("evento-5")))
                .andExpect(content().string(not(containsString("evento-4"))));

        // una pagina fuera de rango muestra la ultima disponible en vez de fallar
        mockMvc.perform(get("/principal/automatizaciones").with(user("admin").roles("ADMIN"))
                .param("tamano", "5").param("pagina", "50").param("periodo", "xyz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("evento-0")));
    }

    @Test
    void vendedorNoEntraAAutomatizaciones() throws Exception {
        mockMvc.perform(get("/principal/automatizaciones").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void contadorDeAlertasPendientesDelMenu() throws Exception {
        mockMvc.perform(get("/api/iot/pendientes").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").isNumber());
    }

    private void guardarEvento(String estado, String detalle) {
        EventoAutomatizacion e = new EventoAutomatizacion();
        e.setTipo("PLAN_PRODUCCION");
        e.setOrigen("manual");
        e.setFechaHora(LocalDateTime.now().minusMinutes(60 - Integer.parseInt(detalle.substring(7))));
        e.setEstado(estado);
        e.setDetalle(detalle);
        e.setMensaje("mensaje " + detalle);
        eventoRepository.save(e);
    }
}
