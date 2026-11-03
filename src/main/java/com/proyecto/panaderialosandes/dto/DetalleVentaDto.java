package com.proyecto.panaderialosandes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetalleVentaDto {

    private ReferenciaIdDto venta_id;
    private double subtotal;
    private int cantidad;
    private ReferenciaIdDto producto_id;
}
