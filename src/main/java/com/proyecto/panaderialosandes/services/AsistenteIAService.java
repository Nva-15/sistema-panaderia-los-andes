package com.proyecto.panaderialosandes.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

// asistente de IA del sistema: envia la conversacion a Google Gemini y ejecuta las herramientas que el modelo pida
// (ciclo de "function calling": el modelo pide datos -> el sistema los consulta -> el modelo responde con esos datos)
@Service
public class AsistenteIAService {

    private static final Logger logger = LoggerFactory.getLogger(AsistenteIAService.class);
    private static final int MAX_VUELTAS_HERRAMIENTAS = 6;
    private static final int MAX_MENSAJES_HISTORIAL = 20;

    private final HerramientasAsistente herramientas;
    private final ObjectMapper objectMapper;
    private final Client cliente;
    private final String modelo;
    private final String nivelRazonamiento;
    // si el modelo configurado no acepta el nivel de razonamiento, se deja de enviar
    private final AtomicBoolean razonamientoSoportado = new AtomicBoolean(true);

    public AsistenteIAService(HerramientasAsistente herramientas, ObjectMapper objectMapper,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:gemini-flash-latest}") String modelo,
            @Value("${app.ia.razonamiento:LOW}") String nivelRazonamiento,
            @Value("${app.ia.base-url:}") String baseUrl) {
        this.herramientas = herramientas;
        this.objectMapper = objectMapper;
        this.modelo = modelo;
        this.nivelRazonamiento = nivelRazonamiento == null ? "" : nivelRazonamiento.trim().toUpperCase();
        // si no hay API key el sistema funciona igual; solo el asistente queda deshabilitado
        if (apiKey == null || apiKey.isBlank()) {
            this.cliente = null;
        } else {
            Client.Builder builder = Client.builder().apiKey(apiKey);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.httpOptions(HttpOptions.builder().baseUrl(baseUrl).build());
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
                    "El asistente de IA no esta configurado. Falta la variable de entorno GEMINI_API_KEY.");
        }

        List<Content> mensajes = construirMensajes(modo, historial);
        if (mensajes.isEmpty()) {
            return AsistenteDto.Respuesta.error("Escribe una pregunta para el asistente.");
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(PromptsAsistente.sistema(modo))))
                .tools(Tool.builder().functionDeclarations(herramientas.definiciones()).build())
                .build();

        List<String> herramientasUsadas = new ArrayList<>();
        long inicioConsulta = System.currentTimeMillis();
        try {
            for (int vuelta = 0; vuelta < MAX_VUELTAS_HERRAMIENTAS; vuelta++) {
                long inicio = System.currentTimeMillis();
                GenerateContentResponse respuesta = generar(mensajes, config);
                logger.info("Asistente: respuesta de Gemini en {} ms (vuelta {})", System.currentTimeMillis() - inicio, vuelta + 1);

                if (respuesta.promptFeedback().flatMap(f -> f.blockReason()).isPresent()) {
                    return AsistenteDto.Respuesta.error("El asistente no puede responder esa solicitud. Intenta reformularla.");
                }
                Candidate candidato = respuesta.candidates().flatMap(c -> c.stream().findFirst()).orElse(null);
                if (candidato == null || candidato.content().isEmpty()) {
                    return AsistenteDto.Respuesta.error("El asistente no genero una respuesta. Intenta reformular la pregunta.");
                }
                FinishReason.Known motivo = candidato.finishReason().map(FinishReason::knownEnum).orElse(null);
                if (motivo != null && motivo != FinishReason.Known.STOP && motivo != FinishReason.Known.MAX_TOKENS) {
                    logger.warn("Respuesta de IA terminada por: {}", motivo);
                    return AsistenteDto.Respuesta.error("El asistente no puede responder esa solicitud. Intenta reformularla.");
                }

                // se devuelve el contenido completo del modelo (incluye sus firmas de razonamiento) para la siguiente vuelta
                Content contenidoModelo = candidato.content().get();
                mensajes.add(contenidoModelo);

                List<FunctionCall> llamadas = respuesta.functionCalls();
                if (llamadas == null || llamadas.isEmpty()) {
                    logger.info("Asistente: consulta completa en {} ms ({} herramientas)",
                            System.currentTimeMillis() - inicioConsulta, herramientasUsadas.size());
                    return AsistenteDto.Respuesta.ok(extraerTexto(contenidoModelo), herramientasUsadas);
                }

                // todos los resultados de las funciones van juntos en un solo mensaje del usuario
                List<Part> resultados = new ArrayList<>();
                for (FunctionCall llamada : llamadas) {
                    String nombre = llamada.name().orElse("");
                    herramientasUsadas.add(nombre);
                    long inicioHerramienta = System.currentTimeMillis();
                    resultados.add(ejecutarHerramienta(llamada, nombre));
                    logger.info("Asistente: herramienta {} en {} ms", nombre, System.currentTimeMillis() - inicioHerramienta);
                }
                mensajes.add(Content.builder().role("user").parts(resultados).build());
            }
            return AsistenteDto.Respuesta.error("La consulta necesito demasiados pasos. Intenta una pregunta mas concreta.");
        } catch (ApiException e) {
            logger.error("Error de la API de IA (HTTP {}): {}", e.code(), e.message());
            // se muestra el detalle que envia Google (cuota, modelo, clave) para poder diagnosticar el problema
            String detalle = " Detalle de Google (HTTP " + e.code() + "): " + resumir(e.message());
            if (e.code() == 429) {
                return AsistenteDto.Respuesta.error(
                        "Se alcanzo el limite gratuito de consultas del modelo " + modelo + "." + detalle);
            }
            return AsistenteDto.Respuesta.error("El servicio de IA respondio con un error." + detalle);
        } catch (GenAiIOException e) {
            logger.error("No se pudo conectar con la API de IA", e);
            return AsistenteDto.Respuesta.error("No se pudo conectar con el servicio de IA. Revisa la conexion.");
        }
    }

    // llama a Gemini con un nivel de razonamiento bajo (respuestas mas rapidas). Si el modelo configurado
    // no admite ese ajuste (error 400), se repite la llamada sin el y no se vuelve a enviar.
    private GenerateContentResponse generar(List<Content> mensajes, GenerateContentConfig config) {
        if (nivelRazonamiento.isEmpty() || !razonamientoSoportado.get()) {
            return cliente.models.generateContent(modelo, mensajes, config);
        }
        GenerateContentConfig conRazonamiento = config.toBuilder()
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(nivelRazonamiento).build())
                .build();
        try {
            return cliente.models.generateContent(modelo, mensajes, conRazonamiento);
        } catch (ApiException e) {
            if (e.code() != 400) {
                throw e;
            }
            logger.warn("El modelo {} no acepto el nivel de razonamiento {}: {}. Se usara el valor por defecto.",
                    modelo, nivelRazonamiento, e.message());
            razonamientoSoportado.set(false);
            return cliente.models.generateContent(modelo, mensajes, config);
        }
    }

    // convierte el historial del chat (solo texto) en mensajes para la API (roles "user" y "model")
    private List<Content> construirMensajes(Modo modo, List<AsistenteDto.Mensaje> historial) {
        List<Content> mensajes = new ArrayList<>();
        List<AsistenteDto.Mensaje> recientes = historial == null ? List.of()
                : historial.subList(Math.max(0, historial.size() - MAX_MENSAJES_HISTORIAL), historial.size());

        for (AsistenteDto.Mensaje m : recientes) {
            if (m.texto() == null || m.texto().isBlank()) {
                continue;
            }
            if ("asistente".equals(m.rol())) {
                // la conversacion debe empezar con un mensaje del usuario
                if (!mensajes.isEmpty()) {
                    mensajes.add(Content.builder().role("model").parts(Part.fromText(m.texto())).build());
                }
            } else {
                mensajes.add(Content.builder().role("user").parts(Part.fromText(m.texto())).build());
            }
        }
        if (mensajes.isEmpty() && modo != Modo.CHAT) {
            mensajes.add(Content.builder().role("user")
                    .parts(Part.fromText(PromptsAsistente.solicitudInicial(modo))).build());
        }
        return mensajes;
    }

    private Part ejecutarHerramienta(FunctionCall llamada, String nombre) {
        Map<String, Object> respuesta;
        try {
            Object datos = herramientas.ejecutar(nombre, llamada.args().orElse(Map.of()));
            // la respuesta de una funcion debe ser un objeto JSON
            respuesta = Map.of("resultado", objectMapper.convertValue(datos, new TypeReference<Object>() {
            }));
        } catch (Exception e) {
            // el error se devuelve al modelo para que pueda corregir la llamada o explicarlo al usuario
            logger.warn("Error ejecutando la herramienta {}: {}", nombre, e.getMessage());
            respuesta = Map.of("error", String.valueOf(e.getMessage()));
        }
        FunctionResponse.Builder resultado = FunctionResponse.builder().name(nombre).response(respuesta);
        llamada.id().ifPresent(resultado::id);
        return Part.builder().functionResponse(resultado.build()).build();
    }

    private static String resumir(String mensaje) {
        if (mensaje == null) {
            return "sin detalle";
        }
        return mensaje.length() > 400 ? mensaje.substring(0, 400) + "..." : mensaje;
    }

        private static String extraerTexto(Content contenido) {
        StringBuilder texto = new StringBuilder();
        for (Part parte : contenido.parts().orElse(List.of())) {
            // se omiten las partes de razonamiento interno del modelo
            if (parte.thought().orElse(false)) {
                continue;
            }
            parte.text().ifPresent(texto::append);
        }
        return texto.toString().trim();
    }
}
