package com.proyecto.panaderialosandes.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.panaderialosandes.models.Productos;

import jakarta.persistence.LockModeType;

public interface ProductoRepository extends JpaRepository<Productos, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Productos p where p.id = :id")
    Optional<Productos> buscarParaVenta(@Param("id") int id);
}
