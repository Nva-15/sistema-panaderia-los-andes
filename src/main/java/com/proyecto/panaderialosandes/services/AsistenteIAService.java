package com.proyecto.panaderialosandes.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;
import com.proyecto.panaderialosandes.services.ia.GeminiProveedor;
import com.proyecto.panaderialosandes.services.ia.GroqProveedor;
import com.proyecto.panaderialosandes.services.ia.ProveedorIA;

// asistente de IA del sistema: elige el proveedor configurado (Groq o Google Gemini).
// IA_PROVEEDOR=groq|gemini fuerza uno; si no se indica, se usa Groq si tiene clave y si no Gemini.
@Service
public class AsistenteIAService {

    private static final Logger logger = LoggerFactory.getLogger(AsistenteIAService.class);

    private final ProveedorIA proveedor;

    public AsistenteIAService(GroqProveedor groq, GeminiProveedor gemini,
            @Value("${app.ia.proveedor:auto}") String preferido) {
        this.proveedor = switch (preferido == null ? "auto" : preferido.trim().toLowerCase()) {
            case "groq" -> groq;
            case "gemini" -> gemini;
            default -> groq.estaConfigurado() || !gemini.estaConfigurado() ? groq : gemini;
        };
        logger.info("Asistente de IA: proveedor {} ({})", proveedor.nombre(),
                proveedor.estaConfigurado() ? "configurado" : "sin API key");
    }

    public boolean estaConfigurado() {
        return proveedor.estaConfigurado();
    }

    public String proveedor() {
        return proveedor.nombre();
    }

    public AsistenteDto.Respuesta responder(Modo modo, List<AsistenteDto.Mensaje> historial) {
        if (!estaConfigurado()) {
            return AsistenteDto.Respuesta.error(
                    "El asistente de IA no esta configurado. Falta la variable de entorno GROQ_API_KEY o GEMINI_API_KEY.");
        }
        return proveedor.responder(modo, historial);
    }
}
