package com.proyecto.panaderialosandes.services;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.repositorios.VentaRepository;

// datos del panel de inicio. El vendedor ve la parte operativa (reponer, alertas, accesos);
// el administrador ademas ve los montos de venta, la comparacion y el grafico de 7 dias.
@Service
public class InicioService {

    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final HerramientasAsistente herramientas;
    private final IotService iotService;
    private final VentaRepository ventaRepository;

    public InicioService(HerramientasAsistente herramientas, IotService iotService, VentaRepository ventaRepository) {
        this.herramientas = herramientas;
        this.iotService = iotService;
        this.ventaRepository = ventaRepository;
    }

    public Map<String, Object> panel(boolean esAdmin) {
        LocalDate hoy = IotService.ahora().toLocalDate();
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("fecha", capitalizar(hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, ES)) + " "
                + hoy.getDayOfMonth() + " de " + hoy.getMonth().getDisplayName(TextStyle.FULL, ES));

        Map<String, Object> ventasHoy = herramientas.consultarVentas(hoy.toString(), hoy.toString(), null);
        panel.put("unidadesHoy", ventasHoy.get("unidades_vendidas"));
        panel.put("ventasRegistradas", ventaRepository.contarDelDia(hoy.toString()));

        if (esAdmin) {
            double totalHoy = (double) ventasHoy.get("total_soles");
            String haceUnaSemana = hoy.minusDays(7).toString();
            double totalSemanaPasada = (double) herramientas.consultarVentas(haceUnaSemana, haceUnaSemana, null).get("total_soles");
            panel.put("totalHoy", totalHoy);
            panel.put("variacion", totalSemanaPasada > 0 ? Math.round((totalHoy - totalSemanaPasada) / totalSemanaPasada * 100) : null);
            panel.put("diaSemanaPasada", hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, ES));
            panel.put("masVendidos", ((List<?>) ventasHoy.get("por_producto")).stream().limit(5).toList());
            panel.put("grafico", graficoSieteDias(hoy));
        }

        List<Map<String, Object>> agotados = herramientas.consultarInventario("agotado");
        List<Map<String, Object>> stockBajo = herramientas.consultarInventario("stock_bajo");
        List<Map<String, Object>> porReponer = new ArrayList<>(agotados);
        porReponer.addAll(stockBajo);
        panel.put("porReponer", porReponer.stream().limit(6).toList());
        panel.put("cantidadAgotados", agotados.size());
        panel.put("cantidadStockBajo", stockBajo.size());

        Map<String, Object> resumenIot = iotService.resumen();
        panel.put("alertas", ((List<?>) resumenIot.get("alertas_pendientes")).stream().limit(4).toList());
        Map<String, Object> estadoIot = iotService.estado(1);
        panel.put("esp32EnLinea", estadoIot.get("enLinea"));
        panel.put("esp32UltimaLectura", estadoIot.get("ultimaLectura"));
        Map<String, Object> vitrina = new LinkedHashMap<>();
        for (Object o : (List<?>) estadoIot.get("variables")) {
            Map<?, ?> v = (Map<?, ?>) o;
            vitrina.put(String.valueOf(v.get("clave")), v.get("valor"));
        }
        panel.put("vitrinaTemp", vitrina.get("vitrina_temp_c"));
        panel.put("vitrinaHum", vitrina.get("vitrina_hum_pct"));
        return panel;
    }

    // total vendido por dia en los ultimos 7 dias (barras con su altura en porcentaje)
    private List<Map<String, Object>> graficoSieteDias(LocalDate hoy) {
        Map<String, Object> ventas = herramientas.consultarVentas(hoy.minusDays(6).toString(), hoy.toString(), null);
        Map<?, ?> porDia = (Map<?, ?>) ventas.get("total_por_dia");
        List<Map<String, Object>> dias = new ArrayList<>();
        double maximo = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            Object total = porDia.get(dia.toString());
            double valor = total == null ? 0 : ((Number) total).doubleValue();
            maximo = Math.max(maximo, valor);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("etiqueta", i == 0 ? "Hoy" : capitalizar(dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, ES)).replace(".", ""));
            d.put("total", valor);
            d.put("hoy", i == 0);
            dias.add(d);
        }
        for (Map<String, Object> d : dias) {
            double valor = (double) d.get("total");
            d.put("altura", maximo == 0 ? 2 : Math.max(2, Math.round(valor / maximo * 100)));
        }
        return dias;
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
