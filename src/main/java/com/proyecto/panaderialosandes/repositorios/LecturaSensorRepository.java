package com.proyecto.panaderialosandes.repositorios;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.panaderialosandes.models.LecturaSensor;

public interface LecturaSensorRepository extends JpaRepository<LecturaSensor, Long> {

    Optional<LecturaSensor> findTopByVariableOrderByFechaHoraDesc(String variable);

    List<LecturaSensor> findByVariableAndFechaHoraAfterOrderByFechaHoraAsc(String variable, LocalDateTime desde);

    @Modifying
    @Transactional
    long deleteByFechaHoraBefore(LocalDateTime fecha);
}
