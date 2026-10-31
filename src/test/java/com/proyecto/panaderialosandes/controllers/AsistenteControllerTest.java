package com.proyecto.panaderialosandes.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// sin GEMINI_API_KEY (como en las pruebas) el asistente debe avisar que no esta configurado, sin romper el sistema
@SpringBootTest
@AutoConfigureMockMvc
class AsistenteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laPaginaDelAsistenteCargaParaUnVendedor() throws Exception {
        mockMvc.perform(get("/principal/asistente").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Asistente Los Andes")))
                .andExpect(content().string(Matchers.containsString("GEMINI_API_KEY")));
    }

    @Test
    void sinApiKeyElChatRespondeQueNoEstaConfigurado() throws Exception {
        mockMvc.perform(post("/api/asistente/chat").with(user("vende").roles("VENDEDOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"CHAT\",\"mensajes\":[{\"rol\":\"usuario\",\"texto\":\"hola\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.texto", Matchers.containsString("no esta configurado")));
    }

    @Test
    void modoInvalidoDevuelveError() throws Exception {
        mockMvc.perform(post("/api/asistente/chat").with(user("vende").roles("VENDEDOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"HACKEAR\",\"mensajes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinSesionNoSePuedeUsarElAsistente() throws Exception {
        mockMvc.perform(post("/api/asistente/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"CHAT\",\"mensajes\":[]}"))
                .andExpect(status().is3xxRedirection());
    }
}
