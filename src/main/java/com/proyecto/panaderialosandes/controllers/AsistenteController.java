package com.proyecto.panaderialosandes.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.AsistenteIAService;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

@Controller
public class AsistenteController {

    private final AsistenteIAService asistenteIAService;

    public AsistenteController(AsistenteIAService asistenteIAService) {
        this.asistenteIAService = asistenteIAService;
    }

    @GetMapping("/principal/asistente") // localhost:8081/principal/asistente
    public String mostrarAsistente(Model model) {
        model.addAttribute("iaConfigurada", asistenteIAService.estaConfigurado());
        return "vista/asistente";
    }

    @PostMapping("/api/asistente/chat")
    @ResponseBody
    public ResponseEntity<AsistenteDto.Respuesta> conversar(@RequestBody AsistenteDto.Peticion peticion) {
        Modo modo;
        try {
            modo = peticion.modo() == null ? Modo.CHAT : Modo.valueOf(peticion.modo());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(AsistenteDto.Respuesta.error("Modo no valido: " + peticion.modo()));
        }
        return ResponseEntity.ok(asistenteIAService.responder(modo, peticion.mensajes()));
    }
}
