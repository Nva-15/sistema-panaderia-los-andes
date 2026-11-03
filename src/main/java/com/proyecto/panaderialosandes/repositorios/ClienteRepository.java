package com.proyecto.panaderialosandes.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proyecto.panaderialosandes.models.Clientes;

public interface ClienteRepository extends JpaRepository<Clientes, Integer>{
    Clientes findByDni(String dni);
}
