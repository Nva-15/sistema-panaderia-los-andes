package com.proyecto.panaderialosandes.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.proyecto.panaderialosandes.config.MqttIotListener;
import com.proyecto.panaderialosandes.services.IotService;

@Controller
public class IotController {

    private final IotService iotService;
    private final MqttIotListener mqtt;
    private final String apiKey;

    public IotController(IotService iotService, MqttIotListener mqtt, @Value("${app.iot.api-key:}") String apiKey) {
        this.iotService = iotService;
        this.mqtt = mqtt;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @GetMapping("/principal/iot") // localhost:8081/principal/iot
    public String mostrarDashboard() {
        return "vista/iot";
    }

    // entrada alternativa por HTTP (la principal es MQTT). Mismo JSON que el ESP32 publica en <base>/telemetria.
    // No requiere sesion: se protege con la cabecera X-API-KEY.
    @PostMapping("/api/iot/telemetria")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recibirTelemetria(
            @RequestHeader(value = "X-API-KEY", required = false) String claveRecibida,
            @RequestBody Map<String, Object> datos) {
        ResponseEntity<Map<String, Object>> rechazo = validarClave(claveRecibida);
        if (rechazo != null) {
            return rechazo;
        }
        int guardadas = iotService.registrarTelemetria(String.valueOf(datos.getOrDefault("nodo", "esp32")), datos);
        if (guardadas == 0) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "No se recibio ninguna variable conocida"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "variables", guardadas));
    }

    // mismo JSON que el ESP32 publica en <base>/alertas: {"nodo", "tipo", "mensaje", "prioridad"}
    @PostMapping("/api/iot/alertas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recibirAlerta(
            @RequestHeader(value = "X-API-KEY", required = false) String claveRecibida,
            @RequestBody Map<String, Object> datos) {
        ResponseEntity<Map<String, Object>> rechazo = validarClave(claveRecibida);
        if (rechazo != null) {
            return rechazo;
        }
        boolean nueva = iotService.registrarAlerta(String.valueOf(datos.getOrDefault("nodo", "esp32")),
                texto(datos.get("tipo")), texto(datos.get("mensaje")), texto(datos.get("prioridad")));
        return ResponseEntity.ok(Map.of("ok", true, "nueva", nueva));
    }

    // datos del dashboard: ultimo valor de cada sensor, series de las ultimas horas y alertas
    @GetMapping("/api/iot/estado")
    @ResponseBody
    public Map<String, Object> estado(@RequestParam(defaultValue = "6") int horas) {
        Map<String, Object> estado = new java.util.LinkedHashMap<>(iotService.estado(Math.max(1, Math.min(horas, 72))));
        estado.put("mqttConectado", mqtt.estaConectado());
        return estado;
    }

    @PostMapping("/api/iot/alertas/{id}/atender")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> atenderAlerta(@PathVariable Long id) {
        return iotService.atenderAlerta(id) ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.notFound().build();
    }

    private ResponseEntity<Map<String, Object>> validarClave(String claveRecibida) {
        if (apiKey.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "Entrada HTTP de IoT deshabilitada: falta la variable IOT_API_KEY"));
        }
        if (claveRecibida == null || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), claveRecibida.trim().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "API key invalida"));
        }
        return null;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
