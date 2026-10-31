package com.proyecto.panaderialosandes.repositorios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.proyecto.panaderialosandes.models.EventoAutomatizacion;

public interface EventoAutomatizacionRepository
        extends JpaRepository<EventoAutomatizacion, Long>, JpaSpecificationExecutor<EventoAutomatizacion> {

    List<EventoAutomatizacion> findTop30ByOrderByFechaHoraDesc();

    Optional<EventoAutomatizacion> findTopByTipoOrderByFechaHoraDesc(String tipo);
}
