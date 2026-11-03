package com.proyecto.panaderialosandes.services;


import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.models.Ventas;
import com.proyecto.panaderialosandes.repositorios.VentaRepository;

@Service
public class VentasServices {
    private final VentaRepository ventaRepository;

    public VentasServices(VentaRepository ventaRepository) {
        this.ventaRepository = ventaRepository;
    }

    public Ventas guardarVenta(Ventas venta) {
        return ventaRepository.save(venta);
    }

}
