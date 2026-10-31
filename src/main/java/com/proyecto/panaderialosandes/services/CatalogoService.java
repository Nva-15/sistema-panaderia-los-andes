package com.proyecto.panaderialosandes.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.proyecto.panaderialosandes.models.Productos;

// catalogo publico de Realidad Aumentada: productos activos con su modelo 3D, precio y disponibilidad en vivo
@Service
public class CatalogoService {

    private static final int POCAS_UNIDADES = 10;

    private final ProductoService productoService;

    public CatalogoService(ProductoService productoService) {
        this.productoService = productoService;
    }

    // solo informacion publica: no se muestra la cantidad exacta en stock
    public List<Map<String, Object>> productos() {
        return productoService.obtenerTodosLosProductos().stream()
                .filter(p -> "activo".equals(p.getEstado()))
                .map(this::comoMapa)
                .toList();
    }

    private Map<String, Object> comoMapa(Productos p) {
        String categoria = p.getCategoria_id() != null ? p.getCategoria_id().getNombre() : "";
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", p.getId());
        datos.put("nombre", p.getNombre());
        datos.put("categoria", categoria);
        datos.put("precio", p.getPrecio());
        datos.put("unidad", p.getUnidad());
        datos.put("disponibilidad", p.getCantidad() <= 0 ? "agotado" : p.getCantidad() <= POCAS_UNIDADES ? "pocas" : "disponible");
        datos.put("modelo", "/modelos/" + modelo(p.getNombre(), categoria) + ".glb");
        return datos;
    }

    // elige el modelo 3D segun el sabor (nombre) y la categoria del producto
    static String modelo(String nombre, String categoria) {
        String n = normalizar(nombre);
        String c = normalizar(categoria);
        if (c.contains("torta") || n.contains("torta")) {
            if (n.contains("chocolate") || n.contains("selva negra") || n.contains("brownie")) {
                return "torta_chocolate";
            }
            if (n.contains("fresa") || n.contains("frutilla") || n.contains("frambuesa")) {
                return "torta_fresa";
            }
            return "torta_tres_leches";
        }
        if (c.contains("pan") || n.contains("pan")) {
            return "pan_frances";
        }
        return "pastel";
    }

    // imagen PNG del codigo QR que apunta al catalogo (para imprimir y pegar en la vitrina)
    public byte[] codigoQr(String url, int tamano) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, tamano, tamano,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el codigo QR", e);
        }
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
