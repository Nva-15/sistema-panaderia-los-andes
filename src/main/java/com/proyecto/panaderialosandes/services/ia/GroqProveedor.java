package com.proyecto.panaderialosandes.services.ia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.HerramientasAsistente;
import com.proyecto.panaderialosandes.services.PromptsAsistente;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

// proveedor Groq (plan gratuito): API compatible con OpenAI "chat completions" con llamadas a herramientas.
// Ciclo: el modelo pide datos (tool_calls) -> el sistema los consulta -> el modelo responde con esos datos.
@Service
public class GroqProveedor implements ProveedorIA {

    private static final Logger logger = LoggerFactory.getLogger(GroqProveedor.class);

    // si el modelo configurado ya no existe en Groq, se elige el primero disponible de esta lista
    private static final List<String> MODELOS_PREFERIDOS = List.of(
            "openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "qwen", "llama");

    private final HerramientasAsistente herramientas;
    private final ObjectMapper objectMapper;
    private final RestClient cliente;
    private final AtomicReference<String> modelo;

    public GroqProveedor(HerramientasAsistente herramientas, ObjectMapper objectMapper,
            @Value("${app.ia.groq.api-key:}") String apiKey,
            @Value("${app.ia.groq.modelo:openai/gpt-oss-120b}") String modelo,
            @Value("${app.ia.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl) {
        this.herramientas = herramientas;
        this.objectMapper = objectMapper;
        this.modelo = new AtomicReference<>(modelo);
        if (apiKey == null || apiKey.isBlank()) {
            this.cliente = null;
        } else {
            SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
            tiempos.setConnectTimeout(10_000);
            tiempos.setReadTimeout(90_000);
            this.cliente = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(tiempos)
                    .defaultHeader("Authorization", "Bearer " + apiKey.trim())
                    .build();
        }
    }

    @Override
    public String nombre() {
        return "Groq (" + modelo.get() + ")";
    }

    @Override
    public boolean estaConfigurado() {
        return cliente != null;
    }

    @Override
    public AsistenteDto.Respuesta responder(Modo modo, List<AsistenteDto.Mensaje> historial) {
        List<Map<String, Object>> mensajes = construirMensajes(modo, historial);
        if (mensajes.size() <= 1) {
            return AsistenteDto.Respuesta.error("Escribe una pregunta para el asistente.");
        }

        List<String> herramientasUsadas = new ArrayList<>();
        long inicioConsulta = System.currentTimeMillis();
        try {
            for (int vuelta = 0; vuelta < MAX_VUELTAS_HERRAMIENTAS; vuelta++) {
                long inicio = System.currentTimeMillis();
                JsonNode respuesta = generar(mensajes);
                logger.info("Asistente: respuesta de Groq en {} ms (vuelta {})", System.currentTimeMillis() - inicio, vuelta + 1);

                JsonNode eleccion = respuesta.path("choices").path(0);
                JsonNode mensaje = eleccion.path("message");
                JsonNode llamadas = mensaje.path("tool_calls");

                if (!llamadas.isArray() || llamadas.isEmpty()) {
                    String texto = mensaje.path("content").asString("").trim();
                    if (texto.isEmpty()) {
                        return AsistenteDto.Respuesta.error("El asistente no genero una respuesta. Intenta reformular la pregunta.");
                    }
                    logger.info("Asistente: consulta completa en {} ms ({} herramientas)",
                            System.currentTimeMillis() - inicioConsulta, herramientasUsadas.size());
                    return AsistenteDto.Respuesta.ok(texto, herramientasUsadas);
                }

                // se devuelve el pedido del modelo (con sus tool_calls) y luego un mensaje "tool" por cada resultado
                Map<String, Object> pedido = new LinkedHashMap<>();
                pedido.put("role", "assistant");
                pedido.put("content", mensaje.path("content").isString() ? mensaje.path("content").asString() : "");
                pedido.put("tool_calls", objectMapper.convertValue(llamadas, new TypeReference<List<Object>>() {
                }));
                mensajes.add(pedido);

                for (JsonNode llamada : llamadas) {
                    String nombre = llamada.path("function").path("name").asString();
                    herramientasUsadas.add(nombre);
                    long inicioHerramienta = System.currentTimeMillis();
                    mensajes.add(Map.of(
                            "role", "tool",
                            "tool_call_id", llamada.path("id").asString(),
                            "content", ejecutarHerramienta(nombre, llamada.path("function").path("arguments").asString("{}"))));
                    logger.info("Asistente: herramienta {} en {} ms", nombre, System.currentTimeMillis() - inicioHerramienta);
                }
            }
            return AsistenteDto.Respuesta.error("La consulta necesito demasiados pasos. Intenta una pregunta mas concreta.");
        } catch (RestClientResponseException e) {
            String cuerpo = e.getResponseBodyAsString();
            logger.error("Error de la API de Groq (HTTP {}): {}", e.getStatusCode().value(), cuerpo);
            String detalle = " Detalle de Groq (HTTP " + e.getStatusCode().value() + "): " + ProveedorIA.resumir(mensajeError(cuerpo));
            if (e.getStatusCode().value() == 429) {
                return AsistenteDto.Respuesta.error("Se alcanzo el limite gratuito de consultas del modelo " + modelo.get() + "." + detalle);
            }
            return AsistenteDto.Respuesta.error("El servicio de IA respondio con un error." + detalle);
        } catch (ResourceAccessException e) {
            logger.error("No se pudo conectar con Groq", e);
            return AsistenteDto.Respuesta.error("No se pudo conectar con el servicio de IA. Revisa la conexion.");
        } catch (RestClientException e) {
            logger.error("Respuesta invalida de Groq", e);
            return AsistenteDto.Respuesta.error("El servicio de IA envio una respuesta invalida. Intenta nuevamente.");
        }
    }

    // llama a la API. Si el modelo ya no existe, elige otro disponible; si el modelo genero una llamada a
    // herramienta mal formada (tool_use_failed), reintenta una vez.
    private JsonNode generar(List<Map<String, Object>> mensajes) {
        try {
            return enviar(mensajes);
        } catch (RestClientResponseException e) {
            String cuerpo = e.getResponseBodyAsString();
            if (esModeloNoDisponible(e.getStatusCode().value(), cuerpo)) {
                String alternativo = elegirModeloDisponible();
                if (alternativo != null && !alternativo.equals(modelo.get())) {
                    logger.warn("El modelo {} no esta disponible en Groq; se usara {}", modelo.get(), alternativo);
                    modelo.set(alternativo);
                    return enviar(mensajes);
                }
            } else if (cuerpo.contains("tool_use_failed")) {
                logger.warn("Groq devolvio tool_use_failed; se reintenta la solicitud");
                return enviar(mensajes);
            }
            throw e;
        }
    }

    private JsonNode enviar(List<Map<String, Object>> mensajes) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo.get());
        cuerpo.put("messages", mensajes);
        cuerpo.put("tools", herramientas.definicionesOpenAI());
        cuerpo.put("tool_choice", "auto");
        cuerpo.put("temperature", 0.3);
        cuerpo.put("max_completion_tokens", 4096);
        return cliente.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);
    }

    private static boolean esModeloNoDisponible(int estado, String cuerpo) {
        return estado == 404 || cuerpo.contains("model_not_found") || cuerpo.contains("model_decommissioned")
                || (cuerpo.contains("model") && cuerpo.contains("does not exist"));
    }

    // consulta la lista de modelos de Groq y elige el primero de la lista de preferidos que este disponible
    private String elegirModeloDisponible() {
        try {
            JsonNode lista = cliente.get().uri("/models").retrieve().body(JsonNode.class);
            List<String> disponibles = new ArrayList<>();
            for (JsonNode m : lista.path("data")) {
                String id = m.path("id").asString();
                boolean esDeTexto = !id.contains("whisper") && !id.contains("guard") && !id.contains("tts")
                        && !id.contains("orpheus") && !id.contains("compound") && !id.contains("playai");
                if (esDeTexto && m.path("active").asBoolean(true)) {
                    disponibles.add(id);
                }
            }
            for (String preferido : MODELOS_PREFERIDOS) {
                for (String id : disponibles) {
                    if (id.equals(preferido) || id.contains(preferido)) {
                        return id;
                    }
                }
            }
            return disponibles.isEmpty() ? null : disponibles.get(0);
        } catch (Exception e) {
            logger.warn("No se pudo obtener la lista de modelos de Groq: {}", e.getMessage());
            return null;
        }
    }

    // mensaje de sistema + historial del chat (roles "user" y "assistant")
    private List<Map<String, Object>> construirMensajes(Modo modo, List<AsistenteDto.Mensaje> historial) {
        List<Map<String, Object>> mensajes = new ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", PromptsAsistente.sistema(modo)));
        for (AsistenteDto.Mensaje m : AsistenteDto.recientes(historial, MAX_MENSAJES_HISTORIAL)) {
            if (m.esDelAsistente()) {
                // la conversacion debe empezar con un mensaje del usuario
                if (mensajes.size() > 1) {
                    mensajes.add(Map.of("role", "assistant", "content", m.texto()));
                }
            } else {
                mensajes.add(Map.of("role", "user", "content", m.texto()));
            }
        }
        if (mensajes.size() == 1 && modo != Modo.CHAT) {
            mensajes.add(Map.of("role", "user", "content", PromptsAsistente.solicitudInicial(modo)));
        }
        return mensajes;
    }

    // ejecuta la herramienta y devuelve el resultado como texto JSON (o el error, para que el modelo lo explique)
    private String ejecutarHerramienta(String nombre, String argumentosJson) {
        try {
            Map<String, Object> argumentos = argumentosJson == null || argumentosJson.isBlank() ? Map.of()
                    : objectMapper.readValue(argumentosJson, new TypeReference<Map<String, Object>>() {
                    });
            return objectMapper.writeValueAsString(herramientas.ejecutar(nombre, argumentos));
        } catch (Exception e) {
            logger.warn("Error ejecutando la herramienta {}: {}", nombre, e.getMessage());
            return "{\"error\": " + objectMapper.valueToTree(String.valueOf(e.getMessage())) + "}";
        }
    }

    private String mensajeError(String cuerpo) {
        try {
            JsonNode error = objectMapper.readTree(cuerpo).path("error");
            return error.path("message").asString(cuerpo);
        } catch (Exception e) {
            return cuerpo;
        }
    }
}
