package com.proyecto.panaderialosandes.models;

import java.util.Arrays;
import java.util.Optional;

// variables que envia el ESP32 de la panaderia (prototipo Wokwi del Grupo 03) y su rango normal.
// Las claves coinciden con los campos del JSON de telemetria que publica el dispositivo por MQTT.
public enum VariableIoT {

    VITRINA_TEMP("vitrina_temp_c", "Temperatura de la vitrina", "°C", null, 25.0, true,
            "Sobre 25 °C los productos de pasteleria se deterioran"),
    VITRINA_HUM("vitrina_hum_pct", "Humedad de la vitrina", "%", null, 70.0, true,
            "Sobre 70 % el pan pierde textura y aparece moho"),
    BANDEJA_PESO("bandeja_peso_g", "Peso de la bandeja", "g", 1500.0, null, true,
            "Celda de carga (simulada con potenciometro) bajo la bandeja de pan"),
    BANDEJA_UNIDADES("bandeja_unidades", "Panes en la bandeja", "unid.", 10.0, null, false,
            "Unidades estimadas a 60 g por pan; con menos de 10 hay que reponer"),
    PUERTA_ABIERTA("puerta_abierta", "Puerta del almacen", "", null, null, false,
            "1 = abierta, 0 = cerrada"),
    CLIENTES("clientes_acumulados", "Clientes detectados (sensor PIR)", "", null, null, false,
            "Conteo acumulado desde que se encendio el dispositivo");

    private final String clave;
    private final String nombre;
    private final String unidad;
    private final Double minimo;
    private final Double maximo;
    private final boolean graficar;
    private final String descripcion;

    VariableIoT(String clave, String nombre, String unidad, Double minimo, Double maximo, boolean graficar,
            String descripcion) {
        this.clave = clave;
        this.nombre = nombre;
        this.unidad = unidad;
        this.minimo = minimo;
        this.maximo = maximo;
        this.graficar = graficar;
        this.descripcion = descripcion;
    }

    public static Optional<VariableIoT> desdeClave(String clave) {
        return Arrays.stream(values()).filter(v -> v.clave.equals(clave)).findFirst();
    }

    public boolean fueraDeRango(double valor) {
        return (minimo != null && valor < minimo) || (maximo != null && valor > maximo);
    }

    public String getClave() {
        return clave;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUnidad() {
        return unidad;
    }

    public Double getMinimo() {
        return minimo;
    }

    public Double getMaximo() {
        return maximo;
    }

    public boolean isGraficar() {
        return graficar;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
