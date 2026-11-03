package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;

// Avance 5: catalogo publico de Realidad Aumentada, QR de la vitrina y recorrido VR
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Productos crear(String nombre, String categoria, int stock, String estado) {
        Productos p = new Productos();
        p.setNombre(nombre);
        p.setCategoria_id(categoriaRepository.findAll().stream().filter(c -> c.getNombre().equals(categoria)).findFirst().orElseThrow());
        p.setCantidad(stock);
        p.setPrecio(45.0);
        p.setUnidad("unidad");
        p.setEstado(estado);
        return productoRepository.save(p);
    }

    @Test
    void elCatalogoEsPublicoYMuestraDisponibilidadSinRevelarElStock() throws Exception {
        crear("Torta Selva Negra AR", "Tortas", 3, "activo");
        crear("Torta de fresa AR", "Tortas", 0, "activo");
        crear("Producto oculto AR", "Pasteles", 50, "inactivo");

        mockMvc.perform(get("/catalogo")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("model-viewer")));

        mockMvc.perform(get("/api/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Torta Selva Negra AR')].disponibilidad").value("pocas"))
                .andExpect(jsonPath("$[?(@.nombre=='Torta Selva Negra AR')].modelo").value("/modelos/torta_chocolate.glb"))
                .andExpect(jsonPath("$[?(@.nombre=='Torta de fresa AR')].disponibilidad").value("agotado"))
                .andExpect(jsonPath("$[?(@.nombre=='Torta de fresa AR')].modelo").value("/modelos/torta_fresa.glb"))
                .andExpect(jsonPath("$[?(@.nombre=='Producto oculto AR')]").isEmpty())
                // la cantidad exacta en stock no se publica
                .andExpect(jsonPath("$[0].cantidad").doesNotExist());
    }

    @Test
    void losModelos3dSonPublicos() throws Exception {
        for (String modelo : new String[] { "torta_chocolate", "torta_fresa", "torta_tres_leches", "pan_frances", "pastel" }) {
            mockMvc.perform(get("/modelos/" + modelo + ".glb")).andExpect(status().isOk());
        }
    }

    @Test
    void elQrDeLaVitrinaApuntaAlCatalogoConLaDireccionPublica() throws Exception {
        byte[] png = mockMvc.perform(get("/catalogo/qr.png")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "sistema-panaderia-los-andes.onrender.com")
                .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        String texto = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)))).getText();
        assertEquals("https://sistema-panaderia-los-andes.onrender.com/catalogo", texto);
    }

    @Test
    void elRecorridoVrRequiereSesion() throws Exception {
        mockMvc.perform(get("/principal/recorrido-vr")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/principal/recorrido-vr").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("a-scene")));
    }
}
