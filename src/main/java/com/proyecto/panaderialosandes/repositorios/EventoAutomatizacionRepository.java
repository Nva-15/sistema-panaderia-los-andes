package com.proyecto.panaderialosandes.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.panaderialosandes.models.EventoAutomatizacion;

public interface EventoAutomatizacionRepository extends JpaRepository<EventoAutomatizacion, Long> {

    List<EventoAutomatizacion> findTop30ByOrderByFechaHoraDesc();
}
