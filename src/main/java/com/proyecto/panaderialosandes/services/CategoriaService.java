package com.proyecto.panaderialosandes.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.models.Categorias;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public List<Categorias> buscartodos(){
        return categoriaRepository.findAll();
    }

    public Object listarTodas() {
        return categoriaRepository.findAll();
    }
}
