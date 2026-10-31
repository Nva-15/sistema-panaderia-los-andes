package com.proyecto.panaderialosandes.dto;

import java.util.List;

// datos que intercambian la pantalla del asistente y el servidor
public final class AsistenteDto {

    private AsistenteDto() {
    }

    // un mensaje del chat: rol "usuario" o "asistente"
    public record Mensaje(String rol, String texto) {
    }

    // peticion: modo (CHAT, PLAN_PRODUCCION, PROMOCION) y el historial de la conversacion
    public record Peticion(String modo, List<Mensaje> mensajes) {
    }

    public record Respuesta(boolean exito, String texto, List<String> herramientasUsadas) {

        public static Respuesta ok(String texto, List<String> herramientasUsadas) {
            return new Respuesta(true, texto, herramientasUsadas);
        }

        public static Respuesta error(String texto) {
            return new Respuesta(false, texto, List.of());
        }
    }
}
