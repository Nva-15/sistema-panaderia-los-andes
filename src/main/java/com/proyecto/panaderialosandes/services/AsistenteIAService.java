package com.proyecto.panaderialosandes.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

// asistente de IA del sistema: envia la conversacion a Claude y ejecuta las herramientas que el modelo pida
// (ciclo de "tool use": el modelo pide datos -> el sistema los consulta -> el modelo responde con esos datos)
@Service
public class AsistenteIAService {

    private static final Logger logger = LoggerFactory.getLogger(AsistenteIAService.class);
    private static final int MAX_VUELTAS_HERRAMIENTAS = 6;
    private static final int MAX_MENSAJES_HISTORIAL = 20;

    private final HerramientasAsistente herramientas;
    private final ObjectMapper objectMapper;
    private final AnthropicClient cliente;
    private final String modelo;
    private final String esfuerzo;

    public AsistenteIAService(HerramientasAsistente herramientas, ObjectMapper objectMapper,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:claude-opus-5-5}") String modelo,
            @Value("${app.ia.esfuerzo:medium}") String esfuerzo,
            @Value("${app.ia.base-url:}") String baseUrl) {
        this.herramientas = herramientas;
        this.objectMapper = objectMapper;
        this.modelo = modelo;
        this.esfuerzo = esfuerzo;
        // si no hay API key el sistema funciona igual; solo el asistente queda deshabilitado
        if (apiKey == null || apiKey.isBlank()) {
            this.cliente = null;
        } else {
            AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            this.cliente = builder.build();
        }
    }

    public boolean estaConfigurado() {
        return cliente != null;
    }

    public AsistenteDto.Respuesta responder(Modo modo, List<AsistenteDto.Mensaje> historial) {
        if (!estaConfigurado()) {
            return AsistenteDto.Respuesta.error(
                    "El asistente de IA no esta configurado. Falta la variable de entorno ANTHROPIC_API_KEY.");
        }

        List<BetaMessageParam> mensajes = construirMensajes(modo, historial);
        if (mensajes.isEmpty()) {
            return AsistenteDto.Respuesta.error("Escribe una pregunta para el asistente.");
        }

        List<String> herramientasUsadas = new ArrayList<>();
        try {
            for (int vuelta = 0; vuelta < MAX_VUELTAS_HERRAMIENTAS; vuelta++) {
                BetaMessage respuesta = cliente.beta().messages().create(construirPeticion(modo, mensajes));
                BetaStopReason motivo = respuesta.stopReason().orElse(null);

                if (BetaStopReason.REFUSAL.equals(motivo)) {
                    return AsistenteDto.Respuesta.error("El asistente no puede responder esa solicitud. Intenta reformularla.");
                }

                // se devuelve la respuesta completa del modelo (incluye sus bloques de razonamiento) para la siguiente vuelta
                mensajes.add(respuesta.toParam());

                if (!BetaStopReason.TOOL_USE.equals(motivo)) {
                    return AsistenteDto.Respuesta.ok(extraerTexto(respuesta), herramientasUsadas);
                }

                List<BetaContentBlockParam> resultados = new ArrayList<>();
                for (BetaContentBlock bloque : respuesta.content()) {
                    bloque.toolUse().ifPresent(uso -> {
                        herramientasUsadas.add(uso.name());
                        resultados.add(ejecutarHerramienta(uso));
                    });
                }
                // todos los resultados de herramientas van juntos en un solo mensaje del usuario
                mensajes.add(BetaMessageParam.ofUserOfContentBlockParams(resultados));
            }
            return AsistenteDto.Respuesta.error("La consulta necesito demasiados pasos. Intenta una pregunta mas concreta.");
        } catch (RateLimitException e) {
            logger.warn("Limite de uso de la API de IA alcanzado", e);
            return AsistenteDto.Respuesta.error("El asistente esta recibiendo muchas consultas. Intenta en un minuto.");
        } catch (AnthropicServiceException e) {
            logger.error("Error de la API de IA (HTTP {})", e.statusCode(), e);
            return AsistenteDto.Respuesta.error("El servicio de IA respondio con un error. Intenta nuevamente.");
        } catch (AnthropicException e) {
            logger.error("No se pudo conectar con la API de IA", e);
            return AsistenteDto.Respuesta.error("No se pudo conectar con el servicio de IA. Revisa la conexion.");
        }
    }

    private MessageCreateParams construirPeticion(Modo modo, List<BetaMessageParam> mensajes) {
        MessageCreateParams.Builder peticion = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(16000L)
                .system(PromptsAsistente.sistema(modo))
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(esfuerzo))
                        .build())
                .messages(mensajes);
        herramientas.definiciones().forEach(peticion::addTool);

        // si el modelo principal rechaza la solicitud, la API la reintenta con el modelo alternativo recomendado
        // (los modelos Haiku no tienen esta opcion)
        if (!modelo.contains("haiku")) {
            peticion.addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return peticion.build();
    }

    // convierte el historial del chat (solo texto) en mensajes para la API
    private List<BetaMessageParam> construirMensajes(Modo modo, List<AsistenteDto.Mensaje> historial) {
        List<BetaMessageParam> mensajes = new ArrayList<>();
        List<AsistenteDto.Mensaje> recientes = historial == null ? List.of()
                : historial.subList(Math.max(0, historial.size() - MAX_MENSAJES_HISTORIAL), historial.size());

        for (AsistenteDto.Mensaje m : recientes) {
            if (m.texto() == null || m.texto().isBlank()) {
                continue;
            }
            if ("asistente".equals(m.rol())) {
                // la conversacion debe empezar con un mensaje del usuario
                if (!mensajes.isEmpty()) {
                    mensajes.add(BetaMessageParam.ofAssistant(m.texto()));
                }
            } else {
                mensajes.add(BetaMessageParam.ofUser(m.texto()));
            }
        }
        if (mensajes.isEmpty() && modo != Modo.CHAT) {
            mensajes.add(BetaMessageParam.ofUser(PromptsAsistente.solicitudInicial(modo)));
        }
        return mensajes;
    }

    private BetaContentBlockParam ejecutarHerramienta(BetaToolUseBlock uso) {
        BetaToolResultBlockParam.Builder resultado = BetaToolResultBlockParam.builder().toolUseId(uso.id());
        try {
            Map<String, Object> entrada = objectMapper.convertValue(
                    uso._input().convert(Object.class), new TypeReference<Map<String, Object>>() {
                    });
            Object datos = herramientas.ejecutar(uso.name(), entrada == null ? Map.of() : entrada);
            resultado.content(objectMapper.writeValueAsString(datos));
        } catch (Exception e) {
            // el error se devuelve al modelo para que pueda corregir la llamada o explicarlo al usuario
            logger.warn("Error ejecutando la herramienta {}: {}", uso.name(), e.getMessage());
            resultado.content("Error: " + e.getMessage()).isError(true);
        }
        return BetaContentBlockParam.ofToolResult(resultado.build());
    }

    private static String extraerTexto(BetaMessage respuesta) {
        StringBuilder texto = new StringBuilder();
        for (BetaContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> {
                if (!texto.isEmpty()) {
                    texto.append("\n\n");
                }
                texto.append(t.text());
            });
        }
        return texto.toString();
    }
}
