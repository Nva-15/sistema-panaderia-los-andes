package com.proyecto.panaderialosandes.services.automatizacion;

import com.proyecto.panaderialosandes.models.AlertaIoT;

// eventos internos que disparan automatizaciones (se publican con ApplicationEventPublisher)
public final class EventosNegocio {

    private EventosNegocio() {
    }

    // una venta dejo el producto con poco stock
    public record StockBajo(String producto, int stockActual, String unidad) {
    }

    // el modulo IoT registro una alerta nueva del ESP32
    public record AlertaIoTRegistrada(AlertaIoT alerta) {
    }
}
