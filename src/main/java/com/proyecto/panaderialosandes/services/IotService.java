package com.proyecto.panaderialosandes.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.panaderialosandes.models.AlertaIoT;
import com.proyecto.panaderialosandes.models.LecturaSensor;
import com.proyecto.panaderialosandes.models.VariableIoT;
import com.proyecto.panaderialosandes.repositorios.AlertaIoTRepository;
import com.proyecto.panaderialosandes.repositorios.LecturaSensorRepository;
import com.proyecto.panaderialosandes.services.automatizacion.EventosNegocio;

// recibe la telemetria y las alertas del ESP32 (por MQTT o HTTP), las guarda y arma los datos del dashboard
@Service
public class IotService {

    private static final Logger logger = LoggerFactory.getLogger(IotService.class);
    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    // la misma alerta pendiente no se repite dentro de este intervalo
    private static final Duration PAUSA_ENTRE_ALERTAS = Duration.ofMinutes(10);
    // si no llega telemetria en este tiempo, el dispositivo se considera desconectado
    private static final Duration TIEMPO_EN_LINEA = Duration.ofMinutes(1);

    // ultimo valor recibido de cada variable (tiempo real, aunque no se haya guardado en la base de datos)
    private record UltimoValor(String dispositivo, double valor, LocalDateTime fecha) {
    }

    private final Map<String, UltimoValor> ultimos = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> ultimoGuardado = new ConcurrentHashMap<>();
    // estado publicado en <base>/estado ("online"/"offline"); el topico base corresponde a un solo dispositivo
    private volatile String estadoConexion = "desconocido";

    private final LecturaSensorRepository lecturaRepository;
    private final AlertaIoTRepository alertaRepository;
    private final ApplicationEventPublisher eventos;
    private final int diasRetencion;
    private final Duration intervaloGuardado;

    public IotService(LecturaSensorRepository lecturaRepository, AlertaIoTRepository alertaRepository,
            ApplicationEventPublisher eventos,
            @Value("${app.iot.dias-retencion:30}") int diasRetencion,
            @Value("${app.iot.segundos-entre-guardados:30}") int segundosEntreGuardados) {
        this.lecturaRepository = lecturaRepository;
        this.alertaRepository = alertaRepository;
        this.eventos = eventos;
        this.diasRetencion = diasRetencion;
        this.intervaloGuardado = Duration.ofSeconds(segundosEntreGuardados);
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }

    // telemetria del dispositivo (mismo JSON que publica el ESP32 en <base>/telemetria).
    // Se ignoran los campos que no son variables conocidas (nodo, wifi_rssi, uptime_s).
    // Devuelve la cantidad de variables reconocidas.
    @Transactional
    public int registrarTelemetria(String dispositivo, Map<String, Object> datos) {
        LocalDateTime fecha = ahora();
        Map<VariableIoT, Double> valores = new LinkedHashMap<>();
        for (VariableIoT variable : VariableIoT.values()) {
            Object dato = datos.get(variable.getClave());
            if (dato instanceof Number numero) {
                valores.put(variable, numero.doubleValue());
            } else if (dato instanceof Boolean booleano) {
                valores.put(variable, booleano ? 1.0 : 0.0);
            }
        }
        valores.forEach((variable, valor) -> ultimos.put(variable.getClave(), new UltimoValor(dispositivo, valor, fecha)));
        estadoConexion = "online";

        // se guarda en la base de datos como maximo una vez por intervalo por dispositivo
        LocalDateTime anterior = ultimoGuardado.get(dispositivo);
        if (!valores.isEmpty() && (anterior == null || !fecha.isBefore(anterior.plus(intervaloGuardado)))) {
            ultimoGuardado.put(dispositivo, fecha);
            valores.forEach((variable, valor) -> {
                LecturaSensor lectura = new LecturaSensor();
                lectura.setDispositivo(dispositivo);
                lectura.setVariable(variable.getClave());
                lectura.setValor(valor);
                lectura.setFechaHora(fecha);
                lectura.setFueraDeRango(variable.fueraDeRango(valor));
                lecturaRepository.save(lectura);
            });
        }
        return valores.size();
    }

    // alerta enviada por el dispositivo (mismo JSON que publica el ESP32 en <base>/alertas).
    // Devuelve false si la misma alerta ya estaba pendiente (no se duplica).
    @Transactional
    public boolean registrarAlerta(String dispositivo, String tipo, String mensaje, String prioridad) {
        LocalDateTime fecha = ahora();
        String tipoAlerta = tipo == null || tipo.isBlank() ? "alerta" : tipo;
        if (alertaRepository.existsByTipoAndAtendidaFalseAndFechaHoraAfter(tipoAlerta, fecha.minus(PAUSA_ENTRE_ALERTAS))) {
            return false;
        }
        AlertaIoT alerta = new AlertaIoT();
        alerta.setDispositivo(dispositivo);
        alerta.setTipo(tipoAlerta);
        alerta.setMensaje(mensaje == null || mensaje.isBlank() ? tipoAlerta : mensaje);
        alerta.setPrioridad(prioridad == null ? "MEDIA" : prioridad.toUpperCase());
        alerta.setFechaHora(fecha);
        alertaRepository.save(alerta);
        logger.warn("Alerta IoT [{}] {}: {}", alerta.getPrioridad(), tipoAlerta, alerta.getMensaje());
        // dispara la automatizacion que reenvia la alerta (Avance 4)
        eventos.publishEvent(new EventosNegocio.AlertaIoTRegistrada(alerta));
        return true;
    }

    // estado de conexion publicado por el dispositivo (mensaje "online" / "offline" de MQTT, ultima voluntad)
    public void registrarConexion(String estado) {
        estadoConexion = estado == null ? "offline" : estado.trim().toLowerCase();
        logger.info("IoT: el dispositivo informa que esta {}", estadoConexion);
    }

    // ultimo valor, estado y serie de cada variable para el dashboard
    public Map<String, Object> estado(int horas) {
        LocalDateTime desde = ahora().minusHours(horas);
        List<Map<String, Object>> variables = new ArrayList<>();
        LocalDateTime ultimaLectura = null;

        for (VariableIoT variable : VariableIoT.values()) {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("clave", variable.getClave());
            datos.put("nombre", variable.getNombre());
            datos.put("unidad", variable.getUnidad());
            datos.put("minimo", variable.getMinimo());
            datos.put("maximo", variable.getMaximo());
            datos.put("graficar", variable.isGraficar());
            datos.put("descripcion", variable.getDescripcion());

            UltimoValor ultimo = ultimoValor(variable);
            if (ultimo != null) {
                datos.put("valor", ultimo.valor());
                datos.put("fecha", ultimo.fecha().format(FECHA_HORA));
                datos.put("estado", variable.fueraDeRango(ultimo.valor()) ? "alerta" : "normal");
                if (ultimaLectura == null || ultimo.fecha().isAfter(ultimaLectura)) {
                    ultimaLectura = ultimo.fecha();
                }
            } else {
                datos.put("estado", "sin_datos");
            }

            List<String> etiquetas = new ArrayList<>();
            List<Double> valores = new ArrayList<>();
            if (variable.isGraficar()) {
                for (LecturaSensor l : lecturaRepository.findByVariableAndFechaHoraAfterOrderByFechaHoraAsc(variable.getClave(), desde)) {
                    etiquetas.add(l.getFechaHora().format(HORA));
                    valores.add(l.getValor());
                }
            }
            datos.put("etiquetas", etiquetas);
            datos.put("valores", valores);
            variables.add(datos);
        }

        boolean desconectadoPorMqtt = "offline".equals(estadoConexion);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("variables", variables);
        resultado.put("enLinea", !desconectadoPorMqtt && ultimaLectura != null
                && ultimaLectura.isAfter(ahora().minus(TIEMPO_EN_LINEA)));
        resultado.put("ultimaLectura", ultimaLectura == null ? null : ultimaLectura.format(FECHA_HORA));
        resultado.put("alertas", alertaRepository.findTop20ByOrderByFechaHoraDesc().stream().map(this::alertaComoMapa).toList());
        return resultado;
    }

    // resumen corto para el asistente de IA: ultimo valor de cada sensor y alertas pendientes
    public Map<String, Object> resumen() {
        List<Map<String, Object>> sensores = new ArrayList<>();
        for (VariableIoT variable : VariableIoT.values()) {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("variable", variable.getNombre());
            datos.put("descripcion", variable.getDescripcion());
            String rango = rango(variable);
            if (rango != null) {
                datos.put("rango_normal", rango);
            }
            UltimoValor ultimo = ultimoValor(variable);
            if (ultimo != null) {
                datos.put("ultimo_valor", ultimo.valor() + (variable.getUnidad().isEmpty() ? "" : " " + variable.getUnidad()));
                datos.put("fecha", ultimo.fecha().format(FECHA_HORA));
                datos.put("fuera_de_rango", variable.fueraDeRango(ultimo.valor()));
            } else {
                datos.put("ultimo_valor", "sin lecturas");
            }
            sensores.add(datos);
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("sensores", sensores);
        resultado.put("alertas_pendientes", alertaRepository.findByAtendidaFalseOrderByFechaHoraDesc().stream()
                .limit(10).map(this::alertaComoMapa).toList());
        return resultado;
    }

    // alertas registradas hoy (para el reporte de cierre diario)
    public List<Map<String, Object>> alertasDelDia() {
        LocalDateTime inicioDelDia = ahora().toLocalDate().atStartOfDay();
        return alertaRepository.findByFechaHoraAfterOrderByFechaHoraDesc(inicioDelDia).stream()
                .map(this::alertaComoMapa).toList();
    }

    @Transactional
    public boolean atenderAlerta(Long id) {
        return alertaRepository.findById(id).map(alerta -> {
            alerta.setAtendida(true);
            alertaRepository.save(alerta);
            return true;
        }).orElse(false);
    }

    // borra las lecturas antiguas todos los dias a las 3:00 (para no llenar la base de datos gratuita)
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    public void limpiarLecturasAntiguas() {
        long borradas = lecturaRepository.deleteByFechaHoraBefore(ahora().minusDays(diasRetencion));
        logger.info("IoT: {} lecturas con mas de {} dias eliminadas", borradas, diasRetencion);
    }

    // ultimo valor en memoria; si el servidor se reinicio, se toma el ultimo guardado en la base de datos
    private UltimoValor ultimoValor(VariableIoT variable) {
        UltimoValor enMemoria = ultimos.get(variable.getClave());
        if (enMemoria != null) {
            return enMemoria;
        }
        return lecturaRepository.findTopByVariableOrderByFechaHoraDesc(variable.getClave())
                .map(l -> new UltimoValor(l.getDispositivo(), l.getValor(), l.getFechaHora()))
                .orElse(null);
    }

    private Map<String, Object> alertaComoMapa(AlertaIoT alerta) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", alerta.getId());
        datos.put("fecha", alerta.getFechaHora().format(FECHA_HORA));
        datos.put("tipo", alerta.getTipo());
        datos.put("mensaje", alerta.getMensaje());
        datos.put("prioridad", alerta.getPrioridad());
        datos.put("atendida", alerta.isAtendida());
        return datos;
    }

    private static String rango(VariableIoT variable) {
        if (variable.getMinimo() == null && variable.getMaximo() == null) {
            return null;
        }
        if (variable.getMaximo() == null) {
            return "minimo " + variable.getMinimo() + " " + variable.getUnidad();
        }
        if (variable.getMinimo() == null) {
            return "maximo " + variable.getMaximo() + " " + variable.getUnidad();
        }
        return variable.getMinimo() + " a " + variable.getMaximo() + " " + variable.getUnidad();
    }
}
