package com.proyecto.panaderialosandes.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// alerta enviada por el dispositivo IoT (temperatura alta, humedad alta, stock bajo en bandeja, puerta abierta)
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "alertas_iot")
public class AlertaIoT {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String dispositivo;
    private String tipo;
    private String mensaje;
    private String prioridad;
    private LocalDateTime fechaHora;
    private boolean atendida;
}
