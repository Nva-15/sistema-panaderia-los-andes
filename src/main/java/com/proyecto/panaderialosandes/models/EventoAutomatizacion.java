package com.proyecto.panaderialosandes.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// registro de cada automatizacion ejecutada (para auditoria y para mostrar en la pantalla de automatizaciones)
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "eventos_automatizacion")
public class EventoAutomatizacion {

    public static final String ENVIADO = "ENVIADO";
    public static final String ERROR = "ERROR";
    public static final String SIN_WEBHOOK = "SIN_WEBHOOK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo;
    private String origen;
    private LocalDateTime fechaHora;
    private String estado;
    @Column(length = 500)
    private String detalle;
    @Column(length = 4000)
    private String mensaje;
}
