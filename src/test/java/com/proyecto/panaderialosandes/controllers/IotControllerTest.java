package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.panaderialosandes.config.MqttIotListener;
import com.proyecto.panaderialosandes.repositorios.AlertaIoTRepository;
import com.proyecto.panaderialosandes.repositorios.LecturaSensorRepository;
import com.proyecto.panaderialosandes.services.HerramientasAsistente;

// pruebas del modulo IoT con el mismo JSON que publica el ESP32 del prototipo Wokwi
@SpringBootTest
@AutoConfigureMockMvc
class IotControllerTest {

    private static final String TELEMETRIA = """
            {"nodo":"esp32-panaderia-01","vitrina_temp_c":27.3,"vitrina_hum_pct":55.0,"bandeja_peso_g":1200,
             "bandeja_unidades":20,"puerta_abierta":false,"clientes_acumulados":4,"wifi_rssi":-60,"uptime_s":120}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MqttIotListener mqtt;

    @Autowired
    private LecturaSensorRepository lecturaRepository;

    @Autowired
    private AlertaIoTRepository alertaRepository;

    @Autowired
    private HerramientasAsistente herramientas;

    @BeforeEach
    void limpiar() {
        lecturaRepository.deleteAll();
        alertaRepository.deleteAll();
    }

    @Test
    void sinClaveNoSeAceptanLecturasPorHttp() throws Exception {
        mockMvc.perform(post("/api/iot/telemetria").contentType(MediaType.APPLICATION_JSON).content(TELEMETRIA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/iot/telemetria").header("X-API-KEY", "clave-incorrecta")
                .contentType(MediaType.APPLICATION_JSON).content(TELEMETRIA))
                .andExpect(status().isUnauthorized());
        assertEquals(0, lecturaRepository.count());
    }

    @Test
    void telemetriaPorHttpGuardaLasSeisVariablesEIgnoraLasDemas() throws Exception {
        mockMvc.perform(post("/api/iot/telemetria").header("X-API-KEY", "clave-iot-pruebas")
                .contentType(MediaType.APPLICATION_JSON).content(TELEMETRIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables").value(6));
        assertEquals(6, lecturaRepository.count());
    }

    @Test
    void telemetriaPorMqttApareceEnElDashboardConSuEstado() throws Exception {
        mqtt.messageArrived("losandes/utpgrupo03/telemetria", new MqttMessage(TELEMETRIA.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/iot/estado").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enLinea").value(true))
                // 27.3 °C supera el maximo de 25 °C de la vitrina
                .andExpect(jsonPath("$.variables[?(@.clave=='vitrina_temp_c')].estado").value("alerta"))
                .andExpect(jsonPath("$.variables[?(@.clave=='vitrina_hum_pct')].estado").value("normal"))
                // 1200 g esta bajo el minimo de 1500 g
                .andExpect(jsonPath("$.variables[?(@.clave=='bandeja_peso_g')].estado").value("alerta"))
                .andExpect(jsonPath("$.variables[?(@.clave=='puerta_abierta')].valor").value(0.0))
                .andExpect(jsonPath("$.variables[?(@.clave=='clientes_acumulados')].valor").value(4.0));
    }

    @Test
    void alertasPorMqttSeGuardanSinDuplicarseYSePuedenAtender() throws Exception {
        String alerta = """
                {"nodo":"esp32-panaderia-01","tipo":"temp_vitrina_alta","mensaje":"Temperatura de vitrina sobre el umbral",
                 "prioridad":"ALTA","uptime_s":130}
                """;
        mqtt.messageArrived("losandes/utpgrupo03/alertas", new MqttMessage(alerta.getBytes(StandardCharsets.UTF_8)));
        mqtt.messageArrived("losandes/utpgrupo03/alertas", new MqttMessage(alerta.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, alertaRepository.count());

        Long id = alertaRepository.findAll().get(0).getId();
        mockMvc.perform(post("/api/iot/alertas/" + id + "/atender").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk());
        assertTrue(alertaRepository.findById(id).orElseThrow().isAtendida());
    }

    @Test
    void mensajeMqttMalFormadoSeIgnoraSinRomperNada() throws Exception {
        mqtt.messageArrived("losandes/utpgrupo03/telemetria", new MqttMessage("esto no es json".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, lecturaRepository.count());
    }

    @Test
    void elDispositivoOfflineSeMuestraDesconectado() throws Exception {
        mqtt.messageArrived("losandes/utpgrupo03/telemetria", new MqttMessage(TELEMETRIA.getBytes(StandardCharsets.UTF_8)));
        mqtt.messageArrived("losandes/utpgrupo03/estado", new MqttMessage("offline".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get("/api/iot/estado").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.enLinea").value(false));
        // al volver a publicar, queda en linea otra vez
        mqtt.messageArrived("losandes/utpgrupo03/estado", new MqttMessage("online".getBytes(StandardCharsets.UTF_8)));
        mqtt.messageArrived("losandes/utpgrupo03/telemetria", new MqttMessage(TELEMETRIA.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get("/api/iot/estado").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.enLinea").value(true));
    }

    @Test
    void elAsistenteDeIaPuedeConsultarLosSensores() throws Exception {
        mqtt.messageArrived("losandes/utpgrupo03/telemetria", new MqttMessage(TELEMETRIA.getBytes(StandardCharsets.UTF_8)));
        Object resumen = herramientas.ejecutar(HerramientasAsistente.CONSULTAR_SENSORES, java.util.Map.of());
        String texto = resumen.toString();
        assertTrue(texto.contains("Temperatura de la vitrina"));
        assertTrue(texto.contains("27.3"));
        assertFalse(texto.contains("sin lecturas"));
    }
}
