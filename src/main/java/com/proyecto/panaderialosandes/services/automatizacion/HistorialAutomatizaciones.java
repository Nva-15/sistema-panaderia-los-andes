package com.proyecto.panaderialosandes.services.automatizacion;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.models.EventoAutomatizacion;
import com.proyecto.panaderialosandes.repositorios.EventoAutomatizacionRepository;
import com.proyecto.panaderialosandes.services.IotService;

// historial de automatizaciones con filtros (tipo, estado, periodo), paginacion e indicadores
@Service
public class HistorialAutomatizaciones {

    public static final List<Integer> TAMANOS = List.of(5, 10, 20, 50);
    public static final List<String> ESTADOS = List.of(EventoAutomatizacion.ENVIADO, EventoAutomatizacion.ERROR,
            EventoAutomatizacion.SIN_WEBHOOK);
    // horarios de las tareas programadas (.github/workflows/automatizaciones.yml)
    private static final Map<TipoAutomatizacion, LocalTime> HORARIOS = Map.of(
            TipoAutomatizacion.PLAN_PRODUCCION, LocalTime.of(19, 0),
            TipoAutomatizacion.CIERRE_DIARIO, LocalTime.of(21, 0));

    private final EventoAutomatizacionRepository repositorio;

    public HistorialAutomatizaciones(EventoAutomatizacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    public record Filtros(String tipo, String estado, String periodo, int tamano, int pagina) {

        public static Filtros normalizar(String tipo, String estado, String periodo, Integer tamano, Integer pagina) {
            String t = TipoAutomatizacion.desdeClave(tipo == null ? "" : tipo).map(TipoAutomatizacion::getClave).orElse("");
            String e = estado != null && ESTADOS.contains(estado) ? estado : "";
            String p = periodo != null && List.of("hoy", "7", "30", "todo").contains(periodo) ? periodo : "7";
            int n = tamano != null && TAMANOS.contains(tamano) ? tamano : 5;
            int pag = pagina == null || pagina < 1 ? 1 : pagina;
            return new Filtros(t, e, p, n, pag);
        }
    }

    public Page<EventoAutomatizacion> buscar(Filtros f) {
        Specification<EventoAutomatizacion> condicion = porTipo(f.tipo()).and(porPeriodo(f.periodo())).and(porEstado(f.estado()));
        Page<EventoAutomatizacion> pagina = repositorio.findAll(condicion, PageRequest.of(f.pagina() - 1, f.tamano(), Sort.by(Sort.Direction.DESC, "fechaHora")));
        // si la pagina pedida ya no existe (por ejemplo al cambiar un filtro) se muestra la ultima
        if (pagina.getContent().isEmpty() && pagina.getTotalPages() > 0 && f.pagina() > pagina.getTotalPages()) {
            pagina = repositorio.findAll(condicion, PageRequest.of(pagina.getTotalPages() - 1, f.tamano(), Sort.by(Sort.Direction.DESC, "fechaHora")));
        }
        return pagina;
    }

    // cantidad por estado con los mismos filtros de tipo y periodo (para los contadores de las pestanas)
    public Map<String, Long> contadores(Filtros f) {
        Specification<EventoAutomatizacion> base = porTipo(f.tipo()).and(porPeriodo(f.periodo()));
        Map<String, Long> contadores = new LinkedHashMap<>();
        contadores.put("", repositorio.count(base));
        ESTADOS.forEach(e -> contadores.put(e, repositorio.count(base.and(porEstado(e)))));
        return contadores;
    }

    public Map<String, Object> indicadores() {
        LocalDateTime inicioHoy = IotService.ahora().toLocalDate().atStartOfDay();
        Specification<EventoAutomatizacion> hoy = (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fechaHora"), inicioHoy);
        Specification<EventoAutomatizacion> semana = porPeriodo("7");
        long totalSemana = repositorio.count(semana);
        long enviadosSemana = repositorio.count(semana.and(porEstado(EventoAutomatizacion.ENVIADO)));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("enviadosHoy", repositorio.count(hoy.and(porEstado(EventoAutomatizacion.ENVIADO))));
        datos.put("erroresHoy", repositorio.count(hoy.and(porEstado(EventoAutomatizacion.ERROR))));
        datos.put("exitoSemana", totalSemana == 0 ? null : Math.round(enviadosSemana * 100.0 / totalSemana));
        datos.put("totalSemana", totalSemana);
        datos.put("enviadosSemana", enviadosSemana);
        datos.putAll(proximaEjecucion());
        return datos;
    }

    // ultima ejecucion de cada automatizacion (para las tarjetas)
    public Map<String, EventoAutomatizacion> ultimas() {
        Map<String, EventoAutomatizacion> ultimas = new LinkedHashMap<>();
        for (TipoAutomatizacion t : TipoAutomatizacion.values()) {
            repositorio.findTopByTipoOrderByFechaHoraDesc(t.getClave()).ifPresent(e -> ultimas.put(t.getClave(), e));
        }
        return ultimas;
    }

    public String horario(TipoAutomatizacion tipo) {
        LocalTime hora = HORARIOS.get(tipo);
        return hora == null ? null : String.format("Todos los días · %d:%02d p. m.", hora.getHour() - 12, hora.getMinute());
    }

    private Map<String, Object> proximaEjecucion() {
        LocalDateTime ahora = IotService.ahora();
        TipoAutomatizacion proxima = null;
        LocalDateTime cuando = null;
        for (Map.Entry<TipoAutomatizacion, LocalTime> h : HORARIOS.entrySet()) {
            LocalDateTime candidata = ahora.toLocalDate().atTime(h.getValue());
            if (!candidata.isAfter(ahora)) {
                candidata = candidata.plusDays(1);
            }
            if (cuando == null || candidata.isBefore(cuando)) {
                cuando = candidata;
                proxima = h.getKey();
            }
        }
        long minutos = java.time.Duration.between(ahora, cuando).toMinutes();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("proximaNombre", proxima.getNombre());
        datos.put("proximaHora", String.format("%d:%02d", cuando.getHour(), cuando.getMinute()));
        datos.put("proximaFalta", minutos >= 60 ? "en " + minutos / 60 + " h " + minutos % 60 + " min" : "en " + minutos + " min");
        return datos;
    }

    private static Specification<EventoAutomatizacion> porTipo(String tipo) {
        return (r, q, cb) -> tipo == null || tipo.isEmpty() ? cb.conjunction() : cb.equal(r.get("tipo"), tipo);
    }

    private static Specification<EventoAutomatizacion> porEstado(String estado) {
        return (r, q, cb) -> estado == null || estado.isEmpty() ? cb.conjunction() : cb.equal(r.get("estado"), estado);
    }

    private static Specification<EventoAutomatizacion> porPeriodo(String periodo) {
        return (r, q, cb) -> {
            LocalDateTime hoy = IotService.ahora().toLocalDate().atStartOfDay();
            return switch (periodo == null ? "todo" : periodo) {
                case "hoy" -> cb.greaterThanOrEqualTo(r.get("fechaHora"), hoy);
                case "7" -> cb.greaterThanOrEqualTo(r.get("fechaHora"), hoy.minusDays(6));
                case "30" -> cb.greaterThanOrEqualTo(r.get("fechaHora"), hoy.minusDays(29));
                default -> cb.conjunction();
            };
        };
    }
}
