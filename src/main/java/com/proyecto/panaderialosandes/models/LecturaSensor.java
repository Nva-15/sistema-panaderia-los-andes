package com.proyecto.panaderialosandes.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// lectura enviada por un dispositivo IoT (ESP32): una fila por variable medida
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "lecturas_sensor", indexes = @Index(name = "idx_lectura_variable_fecha", columnList = "variable, fechaHora"))
public class LecturaSensor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String dispositivo;
    private String variable;
    private double valor;
    private LocalDateTime fechaHora;
    private boolean fueraDeRango;
}
