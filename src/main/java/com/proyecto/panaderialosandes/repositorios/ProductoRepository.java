package com.proyecto.panaderialosandes.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proyecto.panaderialosandes.models.Productos;

public interface ProductoRepository extends JpaRepository<Productos, Integer>{
    
}
