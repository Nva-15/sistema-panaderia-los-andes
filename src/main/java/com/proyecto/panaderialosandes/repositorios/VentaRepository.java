package com.proyecto.panaderialosandes.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.proyecto.panaderialosandes.models.Ventas;

@Repository
public interface VentaRepository extends JpaRepository<Ventas, Integer>{

    // cantidad de ventas de un dia (misma regla de fecha que el reporte de ventas)
    @Query(value = "SELECT COUNT(*) FROM ventas WHERE DATE(fecha) = :fecha", nativeQuery = true)
    long contarDelDia(@Param("fecha") String fecha);
}
