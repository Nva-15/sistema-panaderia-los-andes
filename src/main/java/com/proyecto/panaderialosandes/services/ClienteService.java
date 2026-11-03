package com.proyecto.panaderialosandes.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.repositorios.ClienteRepository;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public Clientes buscarClientePorDni(String dni) {
        return clienteRepository.findByDni(dni);
    }

    public Clientes buscarPorId(Integer id) {
        return clienteRepository.findById(id).orElse(null);
    }

    public List<Clientes> buscarTodosLosClientes() {
        return clienteRepository.findAll();
    }

    public Clientes guardarCliente(Clientes cliente){
        return clienteRepository.save(cliente);
    }
}
