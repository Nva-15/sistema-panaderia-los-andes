package com.proyecto.panaderialosandes.services;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;

@Service
public class ProductoService implements ProductoServiceInterface {
    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<Productos> obtenerTodosLosProductos() {
        return productoRepository.findAll();
    }
    public void guardarProducto(Productos producto) {
        productoRepository.save(producto);
    }
    public void eliminarProducto(int id) {
        productoRepository.deleteById(id);
    }
    public Optional<Productos> obtenerProductoPorId(int id) {
        return productoRepository.findById(id);
    }
    public void actualizarProducto(Productos producto) {
        productoRepository.save(producto);
    }
    
    public List<Productos> buscarPorNombre(String busqueda) {
        //vamos ha convertir a minusculas para que la busqueda sea insensible a mayusculas y minusculas
        String palabraBuscada = busqueda.toLowerCase();
        
        return productoRepository.findAll().stream()
            .filter(p -> p.getNombre().toLowerCase().contains(palabraBuscada))
            .collect(Collectors.toList());
    }

    public void cambiarEstadoUsuario(int id, String nuevoEstado) {
        productoRepository.findById(id).ifPresent(usuario -> {
            usuario.setEstado(nuevoEstado);
            productoRepository.save(usuario);
        });
    }
}
