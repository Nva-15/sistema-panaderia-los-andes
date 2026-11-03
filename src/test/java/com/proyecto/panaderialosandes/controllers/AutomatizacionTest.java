package com.proyecto.panaderialosandes.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.ClienteRepository;
import com.proyecto.panaderialosandes.repositorios.EventoAutomatizacionRepository;
import com.proyecto.panaderialosandes.repositorios.ProductoRepository;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;
import com.proyecto.panaderialosandes.services.IotService;
import com.sun.net.httpserver.HttpServer;

// pruebas de las automatizaciones: un servidor HTTP local hace de webhook de Make y guarda lo que recibe
@SpringBootTest
@AutoConfigureMockMvc
class AutomatizacionTest {

    private static final List<Map<String, Object>> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer MAKE_SIMULADO = iniciarWebhook();

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("app.automatizacion.webhook-url",
                () -> "http://127.0.0.1:" + MAKE_SIMULADO.getAddress().getPort() + "/webhook");
        registro.add("app.automatizacion.api-key", () -> "clave-automatizacion");
        registro.add("app.automatizacion.asincrono", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoAutomatizacionRepository eventoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private IotService iotService;

    @BeforeEach
    void limpiar() {
        RECIBIDOS.clear();
        eventoRepository.deleteAll();
    }

    @AfterAll
    static void detener() {
        MAKE_SIMULADO.stop(0);
    }

    @Test
    void elCierreDiarioProgramadoRequiereClave() throws Exception {
        mockMvc.perform(post("/api/automatizaciones/cierre_diario/ejecutar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/automatizaciones/cierre_diario/ejecutar").header("X-API-KEY", "otra"))
                .andExpect(status().isUnauthorized());
        assertTrue(RECIBIDOS.isEmpty());
    }

    @Test
    void elCierreDiarioSeEnviaAlWebhookConElResumenDelDia() throws Exception {
        mockMvc.perform(post("/api/automatizaciones/cierre_diario/ejecutar").header("X-API-KEY", "clave-automatizacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"));

        assertEquals(1, RECIBIDOS.size());
        Map<String, Object> recibido = RECIBIDOS.get(0);
        assertEquals("cierre_diario", recibido.get("evento"));
        assertTrue(recibido.get("mensaje").toString().contains("Cierre del día"));
        assertTrue(recibido.get("mensaje").toString().contains("Total vendido: S/"));
        assertEquals(1, eventoRepository.count());
    }

    @Test
    void lasAutomatizacionesEnTiempoRealNoSePuedenDispararDesdeLaApiProgramada() throws Exception {
        mockMvc.perform(post("/api/automatizaciones/stock_bajo/ejecutar").header("X-API-KEY", "clave-automatizacion"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinIaConfiguradaElAgenteRegistraElErrorYNoEnviaNada() throws Exception {
        mockMvc.perform(post("/api/automatizaciones/plan_produccion/ejecutar").header("X-API-KEY", "clave-automatizacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ERROR"));
        assertTrue(RECIBIDOS.isEmpty());
    }

    @Test
    void unaVentaQueDejaPocoStockAvisaUnaSolaVez() throws Exception {
        Productos producto = new Productos();
        producto.setNombre("Pan chabata");
        producto.setCategoria_id(categoriaRepository.findAll().get(0));
        producto.setCantidad(12);
        producto.setPrecio(0.80);
        producto.setUnidad("unidad");
        producto = productoRepository.save(producto);

        Clientes cliente = new Clientes();
        cliente.setNombre("Cliente Automatizacion");
        cliente.setDni("11223344");
        cliente = clienteRepository.save(cliente);
        Usuarios vendedor = usuarioRepository.findByUsername("admin").orElseThrow();

        String venta = """
                {"cliente_id": {"id": %d}, "detalles": [{"producto_id": {"id": %d}, "cantidad": 3, "subtotal": 2.40}], "total": 2.40}
                """.formatted(cliente.getId(), producto.getId());

        // 12 -> 9: cruza el minimo de 10 y avisa
        mockMvc.perform(post("/venta").with(user(vendedor)).contentType(MediaType.APPLICATION_JSON).content(venta))
                .andExpect(status().isOk());
        // 9 -> 6: ya estaba bajo el minimo, no se repite el aviso
        mockMvc.perform(post("/venta").with(user(vendedor)).contentType(MediaType.APPLICATION_JSON).content(venta))
                .andExpect(status().isOk());

        List<Map<String, Object>> avisos = RECIBIDOS.stream().filter(r -> "stock_bajo".equals(r.get("evento"))).toList();
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).get("mensaje").toString().contains("Pan chabata – quedan 9"));
    }

    @Test
    void unaAlertaNuevaDelEsp32SeReenviaAlInstante() {
        iotService.registrarAlerta("esp32-panaderia-01", "humedad_alta", "Humedad de vitrina sobre el umbral", "MEDIA");
        // la misma alerta pendiente no se reenvia
        iotService.registrarAlerta("esp32-panaderia-01", "humedad_alta", "Humedad de vitrina sobre el umbral", "MEDIA");

        List<Map<String, Object>> avisos = RECIBIDOS.stream().filter(r -> "alerta_iot".equals(r.get("evento"))).toList();
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).get("mensaje").toString().contains("Humedad de vitrina sobre el umbral"));
    }

    @Test
    void laPantallaEsSoloParaAdministradores() throws Exception {
        mockMvc.perform(get("/principal/automatizaciones").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/principal/automatizaciones").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/principal/automatizaciones/cierre_diario/ejecutar").with(user("vende").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    private static HttpServer iniciarWebhook() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ObjectMapper mapper = new ObjectMapper();
            servidor.createContext("/webhook", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECIBIDOS.add(mapper.readValue(cuerpo, new TypeReference<Map<String, Object>>() {
                }));
                byte[] respuesta = "Accepted".getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(200, respuesta.length);
                intercambio.getResponseBody().write(respuesta);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
