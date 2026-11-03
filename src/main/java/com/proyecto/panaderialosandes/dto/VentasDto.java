package com.proyecto.panaderialosandes.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentasDto {

    private ReferenciaIdDto cliente_id;
    private List<DetalleVentaDto> detalles;
    private double total;
}