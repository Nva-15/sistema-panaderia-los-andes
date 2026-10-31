package com.proyecto.panaderialosandes.repositorios;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.panaderialosandes.models.AlertaIoT;

public interface AlertaIoTRepository extends JpaRepository<AlertaIoT, Long> {

    List<AlertaIoT> findTop20ByOrderByFechaHoraDesc();

    List<AlertaIoT> findByAtendidaFalseOrderByFechaHoraDesc();

    boolean existsByTipoAndAtendidaFalseAndFechaHoraAfter(String tipo, LocalDateTime desde);
}
