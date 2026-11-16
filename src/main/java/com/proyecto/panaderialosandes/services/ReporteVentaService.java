package com.proyecto.panaderialosandes.services;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.List;

@Service
public class ReporteVentaService {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Object[]> obtenerDatosVentas(String fechaDesde, String fechaHasta, 
                                           Long categoriaId, Long usuarioId, Long clienteId) {
        
        String sql = "SELECT " +
                     "v.id as venta_id, " +
                     "v.fecha as fecha, " +
                     "u.nombre as usuario, " +
                     "cl.nombre as cliente, " +
                     "cl.dni as dni, " +
                     "p.nombre as producto, " +
                     "c.nombre as categoria, " +
                     "dv.cantidad as cantidad, " +
                     "(dv.subtotal / dv.cantidad) as precio, " +
                     "dv.subtotal as subtotal " +
                     "FROM detalleventas dv " +
                     "LEFT JOIN ventas v ON dv.venta_id = v.id " +
                     "LEFT JOIN usuarios u ON v.usuario_id = u.id " +
                     "LEFT JOIN clientes cl ON v.cliente_id = cl.id " +
                     "LEFT JOIN productos p ON dv.producto_id = p.id " +
                     "LEFT JOIN categorias c ON p.categoria_id = c.id " +
                     "WHERE DATE(v.fecha) BETWEEN :fechaDesde AND :fechaHasta ";
        
        if (categoriaId != null) {
            sql += "AND c.id = :categoriaId ";
        }
        if (usuarioId != null) {
            sql += "AND u.id = :usuarioId ";
        }
        if (clienteId != null) {
            sql += "AND v.cliente_id = :clienteId ";
        }
        
        sql += "ORDER BY v.fecha DESC";
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("fechaDesde", fechaDesde);
        query.setParameter("fechaHasta", fechaHasta);
        
        if (categoriaId != null) {
            query.setParameter("categoriaId", categoriaId);
        }
        if (usuarioId != null) {
            query.setParameter("usuarioId", usuarioId);
        }
        if (clienteId != null) {
            query.setParameter("clienteId", clienteId);
        }
        
        return query.getResultList();

        
    }
}
