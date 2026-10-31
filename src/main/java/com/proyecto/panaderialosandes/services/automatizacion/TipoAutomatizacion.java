package com.proyecto.panaderialosandes.services.automatizacion;

import java.util.Arrays;
import java.util.Optional;

// procesos repetitivos de la panaderia que se automatizan
public enum TipoAutomatizacion {

    CIERRE_DIARIO("cierre_diario", "Reporte de cierre diario", "fa-cash-register", true,
            "Todos los dias a las 9:00 p. m. envia el total vendido, los productos mas vendidos, el stock bajo y las alertas IoT del dia."),
    PLAN_PRODUCCION("plan_produccion", "Agente IA: plan de produccion", "fa-robot", true,
            "Todos los dias a las 7:00 p. m. el agente de IA analiza las ventas de 14 dias y el inventario, y envia cuanto hornear manana."),
    STOCK_BAJO("stock_bajo", "Aviso de stock bajo", "fa-boxes-stacked", false,
            "Cuando una venta deja un producto con 10 unidades o menos, avisa al instante para reponerlo."),
    ALERTA_IOT("alerta_iot", "Alertas de sensores IoT", "fa-microchip", false,
            "Reenvia al instante cada alerta del ESP32: vitrina caliente o humeda, bandeja casi vacia, puerta abierta.");

    private final String clave;
    private final String nombre;
    private final String icono;
    private final boolean programada;
    private final String descripcion;

    TipoAutomatizacion(String clave, String nombre, String icono, boolean programada, String descripcion) {
        this.clave = clave;
        this.nombre = nombre;
        this.icono = icono;
        this.programada = programada;
        this.descripcion = descripcion;
    }

    public static Optional<TipoAutomatizacion> desdeClave(String clave) {
        return Arrays.stream(values()).filter(t -> t.clave.equals(clave)).findFirst();
    }

    public String getClave() {
        return clave;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIcono() {
        return icono;
    }

    public boolean isProgramada() {
        return programada;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
