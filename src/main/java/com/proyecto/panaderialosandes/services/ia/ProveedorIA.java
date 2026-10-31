package com.proyecto.panaderialosandes.services.ia;

import java.util.List;

import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

// proveedor de inteligencia artificial del asistente (Groq o Google Gemini).
// Cada proveedor usa los mismos prompts y las mismas herramientas; solo cambia el formato de la API.
public interface ProveedorIA {

    int MAX_VUELTAS_HERRAMIENTAS = 6;
    int MAX_MENSAJES_HISTORIAL = 20;

    String nombre();

    boolean estaConfigurado();

    AsistenteDto.Respuesta responder(Modo modo, List<AsistenteDto.Mensaje> historial);

    static String resumir(String mensaje) {
        if (mensaje == null) {
            return "sin detalle";
        }
        return mensaje.length() > 400 ? mensaje.substring(0, 400) + "..." : mensaje;
    }
}
