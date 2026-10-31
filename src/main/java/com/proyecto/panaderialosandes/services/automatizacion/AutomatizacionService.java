package com.proyecto.panaderialosandes.services.automatizacion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.proyecto.panaderialosandes.dto.AsistenteDto;
import com.proyecto.panaderialosandes.models.AlertaIoT;
import com.proyecto.panaderialosandes.models.EventoAutomatizacion;
import com.proyecto.panaderialosandes.repositorios.EventoAutomatizacionRepository;
import com.proyecto.panaderialosandes.services.AsistenteIAService;
import com.proyecto.panaderialosandes.services.HerramientasAsistente;
import com.proyecto.panaderialosandes.services.IotService;
import com.proyecto.panaderialosandes.services.PromptsAsistente.Modo;

import jakarta.annotation.PreDestroy;

// automatizaciones de la panaderia: arma el mensaje de cada proceso y lo envia por webhook a la plataforma
// No-Code (Make), que lo reparte a Telegram, correo o Google Sheets. Cada ejecucion queda registrada.
@Service
public class AutomatizacionService {

    private static final Logger logger = LoggerFactory.getLogger(AutomatizacionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HerramientasAsistente herramientas;
    private final AsistenteIAService asistente;
    private final IotService iotService;
    private final EventoAutomatizacionRepository eventoRepository;
    private final RestClient cliente;
    private final String webhookUrl;
    private final boolean asincrono;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "automatizaciones");
        hilo.setDaemon(true);
        return hilo;
    });

    public AutomatizacionService(HerramientasAsistente herramientas, AsistenteIAService asistente, IotService iotService,
            EventoAutomatizacionRepository eventoRepository,
            @Value("${app.automatizacion.webhook-url:}") String webhookUrl,
            @Value("${app.automatizacion.asincrono:true}") boolean asincrono) {
        this.herramientas = herramientas;
        this.asistente = asistente;
        this.iotService = iotService;
        this.eventoRepository = eventoRepository;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.asincrono = asincrono;
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(10_000);
        tiempos.setReadTimeout(30_000);
        this.cliente = RestClient.builder().requestFactory(tiempos).build();
    }

    public boolean webhookConfigurado() {
        return !webhookUrl.isEmpty();
    }

    public List<EventoAutomatizacion> recientes() {
        return eventoRepository.findTop30ByOrderByFechaHoraDesc();
    }

    // ejecuta una automatizacion programada o de prueba (desde la pantalla o desde GitHub Actions)
    public EventoAutomatizacion ejecutar(TipoAutomatizacion tipo, String origen) {
        return switch (tipo) {
            case CIERRE_DIARIO -> enviarCierreDiario(origen);
            case PLAN_PRODUCCION -> enviarPlanProduccion(origen);
            case STOCK_BAJO -> enviar(tipo, origen, "Prueba: aviso de stock bajo",
                    "🧪 Mensaje de prueba\n⚠️ Stock bajo: Pan frances (8 unidades). Reponer pronto.", Map.of("prueba", true));
            case ALERTA_IOT -> enviar(tipo, origen, "Prueba: alerta IoT",
                    "🧪 Mensaje de prueba\n🚨 [ALTA] Temperatura de vitrina sobre el umbral", Map.of("prueba", true));
        };
    }

    // ---------- Reporte de cierre diario ----------
    public EventoAutomatizacion enviarCierreDiario(String origen) {
        String hoy = IotService.ahora().toLocalDate().toString();
        Map<String, Object> ventas = herramientas.consultarVentas(hoy, hoy, null);
        List<Map<String, Object>> stockBajo = herramientas.consultarInventario("stock_bajo");
        List<Map<String, Object>> agotados = herramientas.consultarInventario("agotado");
        List<Map<String, Object>> alertas = iotService.alertasDelDia();

        StringBuilder texto = new StringBuilder();
        texto.append("🥖 Cierre del día ").append(LocalDate.parse(hoy).format(FECHA)).append(" – Panadería Los Andes\n\n");
        texto.append("💰 Total vendido: S/ ").append(String.format("%.2f", ventas.get("total_soles"))).append("\n");
        texto.append("📦 Unidades vendidas: ").append(ventas.get("unidades_vendidas")).append("\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productos = (List<Map<String, Object>>) ventas.get("por_producto");
        if (!productos.isEmpty()) {
            texto.append("\n🏆 Más vendidos:\n");
            productos.stream().limit(5).forEach(p -> texto.append("• ").append(p.get("producto")).append(": ")
                    .append(p.get("unidades")).append(" u. (S/ ").append(String.format("%.2f", p.get("monto_soles"))).append(")\n"));
        }
        if (!stockBajo.isEmpty() || !agotados.isEmpty()) {
            texto.append("\n⚠️ Reponer:\n");
            agotados.forEach(p -> texto.append("• ").append(p.get("producto")).append(": AGOTADO\n"));
            stockBajo.forEach(p -> texto.append("• ").append(p.get("producto")).append(": ").append(p.get("stock")).append(" u.\n"));
        }
        texto.append("\n🌡️ Alertas IoT del día: ").append(alertas.size()).append("\n");
        alertas.stream().limit(5).forEach(a -> texto.append("• [").append(a.get("prioridad")).append("] ").append(a.get("mensaje")).append("\n"));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("fecha", hoy);
        datos.put("total_soles", ventas.get("total_soles"));
        datos.put("unidades_vendidas", ventas.get("unidades_vendidas"));
        datos.put("productos_stock_bajo", stockBajo.size());
        datos.put("productos_agotados", agotados.size());
        datos.put("alertas_iot", alertas.size());
        return enviar(TipoAutomatizacion.CIERRE_DIARIO, origen, "Cierre del día " + hoy, texto.toString().trim(), datos);
    }

    // ---------- Agente IA: plan de produccion ----------
    // el agente decide por si mismo que datos consultar (ventas, inventario) y redacta el plan
    public EventoAutomatizacion enviarPlanProduccion(String origen) {
        AsistenteDto.Respuesta respuesta = asistente.responder(Modo.PLAN_PRODUCCION, List.of());
        if (!respuesta.exito()) {
            return registrar(TipoAutomatizacion.PLAN_PRODUCCION, origen, EventoAutomatizacion.ERROR,
                    "El agente de IA no pudo generar el plan: " + respuesta.texto(), null);
        }
        String manana = IotService.ahora().toLocalDate().plusDays(1).format(FECHA);
        String texto = "🤖 Plan de producción para mañana " + manana + "\n\n" + respuesta.texto();
        return enviar(TipoAutomatizacion.PLAN_PRODUCCION, origen, "Plan de producción " + manana, texto,
                Map.of("herramientas_usadas", respuesta.herramientasUsadas()));
    }

    // ---------- Eventos en tiempo real ----------
    @EventListener
    public void alStockBajo(EventosNegocio.StockBajo evento) {
        String texto = evento.stockActual() <= 0
                ? "❌ Producto AGOTADO: " + evento.producto() + ". Reponer pronto."
                : "⚠️ Stock bajo: " + evento.producto() + " – quedan " + evento.stockActual() + ". Reponer pronto.";
        enEsperaOAhora(() -> enviar(TipoAutomatizacion.STOCK_BAJO, "venta", "Stock bajo: " + evento.producto(), texto,
                Map.of("producto", evento.producto(), "stock", evento.stockActual())));
    }

    @EventListener
    public void alRegistrarAlertaIot(EventosNegocio.AlertaIoTRegistrada evento) {
        AlertaIoT alerta = evento.alerta();
        String icono = "ALTA".equals(alerta.getPrioridad()) ? "🚨" : "⚠️";
        String texto = icono + " Alerta IoT [" + alerta.getPrioridad() + "]\n" + alerta.getMensaje()
                + "\nDispositivo: " + alerta.getDispositivo();
        enEsperaOAhora(() -> enviar(TipoAutomatizacion.ALERTA_IOT, "esp32", "Alerta IoT: " + alerta.getTipo(), texto,
                Map.of("tipo", alerta.getTipo(), "prioridad", alerta.getPrioridad())));
    }

    // ---------- Envio al webhook de Make ----------
    // payload: {"evento", "titulo", "mensaje" (texto listo para Telegram/correo), "fecha", "datos"}
    EventoAutomatizacion enviar(TipoAutomatizacion tipo, String origen, String titulo, String mensaje, Map<String, Object> datos) {
        if (!webhookConfigurado()) {
            return registrar(tipo, origen, EventoAutomatizacion.SIN_WEBHOOK,
                    "Falta la variable AUTOMATIZACION_WEBHOOK_URL; el mensaje no se envio", mensaje);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("evento", tipo.getClave());
        cuerpo.put("titulo", titulo);
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("fecha", IotService.ahora().toString());
        cuerpo.put("datos", datos == null ? Map.of() : datos);
        try {
            cliente.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve().toBodilessEntity();
            return registrar(tipo, origen, EventoAutomatizacion.ENVIADO, "Enviado al webhook", mensaje);
        } catch (Exception e) {
            logger.warn("No se pudo enviar la automatizacion {} al webhook: {}", tipo.getClave(), e.getMessage());
            return registrar(tipo, origen, EventoAutomatizacion.ERROR, "Error al enviar al webhook: " + e.getMessage(), mensaje);
        }
    }

    private EventoAutomatizacion registrar(TipoAutomatizacion tipo, String origen, String estado, String detalle, String mensaje) {
        EventoAutomatizacion evento = new EventoAutomatizacion();
        evento.setTipo(tipo.getClave());
        evento.setOrigen(origen);
        evento.setFechaHora(IotService.ahora());
        evento.setEstado(estado);
        evento.setDetalle(recortar(detalle, 500));
        evento.setMensaje(recortar(mensaje, 4000));
        logger.info("Automatizacion {} ({}): {}", tipo.getClave(), origen, estado);
        return eventoRepository.save(evento);
    }

    // los avisos en tiempo real se envian en segundo plano para no demorar la venta ni la recepcion MQTT
    private void enEsperaOAhora(Runnable tarea) {
        if (asincrono) {
            ejecutor.submit(() -> {
                try {
                    tarea.run();
                } catch (Exception e) {
                    logger.error("Error en automatizacion en segundo plano", e);
                }
            });
        } else {
            tarea.run();
        }
    }

    @PreDestroy
    public void detener() {
        ejecutor.shutdown();
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() > maximo ? texto.substring(0, maximo - 3) + "..." : texto;
    }
}
