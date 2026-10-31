package com.proyecto.panaderialosandes.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.proyecto.panaderialosandes.services.CatalogoService;

// Avance 5: catalogo en Realidad Aumentada (publico, para clientes) y gemelo digital en VR (para el personal)
@Controller
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/catalogo") // pagina publica que abre el cliente al escanear el QR
    public String catalogo() {
        return "vista/catalogo";
    }

    @GetMapping("/api/catalogo")
    @ResponseBody
    public List<Map<String, Object>> productos() {
        return catalogoService.productos();
    }

    // codigo QR que apunta al catalogo de esta misma instalacion (sirve en local y en la nube)
    @GetMapping(value = "/catalogo/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> codigoQr() {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/catalogo").toUriString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(catalogoService.codigoQr(url, 600));
    }

    @GetMapping("/catalogo/qr") // cartel imprimible para la vitrina
    public String cartelQr(Model model) {
        model.addAttribute("urlCatalogo", ServletUriComponentsBuilder.fromCurrentContextPath().path("/catalogo").toUriString());
        return "vista/catalogo_qr";
    }

    @GetMapping("/principal/recorrido-vr") // gemelo digital de la panaderia (requiere sesion)
    public String recorridoVr() {
        return "vista/recorrido_vr";
    }
}
