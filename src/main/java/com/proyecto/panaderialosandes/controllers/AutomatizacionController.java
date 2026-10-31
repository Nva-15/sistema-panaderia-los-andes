package com.proyecto.panaderialosandes.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.panaderialosandes.models.EventoAutomatizacion;
import com.proyecto.panaderialosandes.services.automatizacion.AutomatizacionService;
import com.proyecto.panaderialosandes.services.automatizacion.TipoAutomatizacion;

@Controller
public class AutomatizacionController {

    private final AutomatizacionService automatizacionService;
    private final String apiKey;

    public AutomatizacionController(AutomatizacionService automatizacionService,
            @Value("${app.automatizacion.api-key:}") String apiKey) {
        this.automatizacionService = automatizacionService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @GetMapping("/principal/automatizaciones") // localhost:8081/principal/automatizaciones (solo ADMIN)
    public String mostrar(Model model) {
        model.addAttribute("tipos", TipoAutomatizacion.values());
        model.addAttribute("eventos", automatizacionService.recientes());
        model.addAttribute("webhookConfigurado", automatizacionService.webhookConfigurado());
        model.addAttribute("apiKeyConfigurada", !apiKey.isEmpty());
        return "vista/automatizaciones";
    }

    // boton "Ejecutar ahora" de la pantalla (en los avisos en tiempo real envia un mensaje de prueba)
    @PostMapping("/principal/automatizaciones/{clave}/ejecutar")
    public String ejecutarDesdePantalla(@PathVariable String clave, RedirectAttributes redirect) {
        TipoAutomatizacion tipo = TipoAutomatizacion.desdeClave(clave).orElse(null);
        if (tipo == null) {
            redirect.addFlashAttribute("error", "Automatizacion desconocida: " + clave);
        } else {
            EventoAutomatizacion evento = automatizacionService.ejecutar(tipo, "manual");
            redirect.addFlashAttribute(EventoAutomatizacion.ENVIADO.equals(evento.getEstado()) ? "exito" : "error",
                    tipo.getNombre() + ": " + evento.getDetalle());
        }
        return "redirect:/principal/automatizaciones";
    }

    // usado por el flujo programado de GitHub Actions (.github/workflows/automatizaciones.yml).
    // No requiere sesion: se protege con la cabecera X-API-KEY. Solo para las automatizaciones programadas.
    @PostMapping("/api/automatizaciones/{clave}/ejecutar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> ejecutarProgramada(@PathVariable String clave,
            @RequestHeader(value = "X-API-KEY", required = false) String claveRecibida) {
        if (apiKey.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "Falta la variable AUTOMATIZACION_API_KEY"));
        }
        if (claveRecibida == null || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), claveRecibida.trim().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "API key invalida"));
        }
        TipoAutomatizacion tipo = TipoAutomatizacion.desdeClave(clave).filter(TipoAutomatizacion::isProgramada).orElse(null);
        if (tipo == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Automatizacion programada desconocida: " + clave));
        }
        EventoAutomatizacion evento = automatizacionService.ejecutar(tipo, "programada");
        return ResponseEntity.ok(Map.of(
                "ok", EventoAutomatizacion.ENVIADO.equals(evento.getEstado()),
                "estado", evento.getEstado(),
                "detalle", evento.getDetalle()));
    }
}
