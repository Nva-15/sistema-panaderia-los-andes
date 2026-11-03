package com.proyecto.panaderialosandes.controllers;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.proyecto.panaderialosandes.dto.ClienteDto;
import com.proyecto.panaderialosandes.dto.VentasDto;
import com.proyecto.panaderialosandes.dto.VentasExportDto;
import com.proyecto.panaderialosandes.models.Clientes;
import com.proyecto.panaderialosandes.models.DetalleVentas;
import com.proyecto.panaderialosandes.models.Productos;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.models.Ventas;
import com.proyecto.panaderialosandes.services.ClienteService;

import com.proyecto.panaderialosandes.services.DetalleVentaService;
import com.proyecto.panaderialosandes.services.ProductoService;
import com.proyecto.panaderialosandes.services.VentasServices;
import com.proyecto.panaderialosandes.services.automatizacion.EventosNegocio;

import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequestMapping("/venta")
public class VentasController {

    private final Logger logger = LoggerFactory.getLogger(VentasController.class);

    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final VentasServices ventasServices;
    private final DetalleVentaService detalleVentaService;
    private final ApplicationEventPublisher eventos;

    // con esta cantidad o menos se considera stock bajo (igual que en el reporte de inventario)
    private static final int STOCK_MINIMO = 10;

    public VentasController(ClienteService clienteService, ProductoService productoService,
            VentasServices ventasServices, DetalleVentaService detalleVentaService,
            ApplicationEventPublisher eventos) {
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.ventasServices = ventasServices;
        this.detalleVentaService = detalleVentaService;
        this.eventos = eventos;
    }

    @GetMapping("/datosventas")
    @ResponseBody
    public ResponseEntity<VentasExportDto> enviarDatos() {
        List<Clientes> clientes = clienteService.buscarTodosLosClientes();
        // los productos desactivados en el inventario no se ofrecen en la venta
        List<Productos> productos = productoService.obtenerTodosLosProductos().stream()
                .filter(p -> !"inactivo".equalsIgnoreCase(p.getEstado()))
                .toList();
        VentasExportDto data = new VentasExportDto(clientes, productos);

        return ResponseEntity.ok(data);
    }

    @PostMapping("/guardar_cliente")
    @ResponseBody
    public ResponseEntity<Clientes> buscarCliente(@RequestBody ClienteDto clienteDto) {

        Clientes cliente = new Clientes();

        cliente.setDni(clienteDto.getDni());
        cliente.setNombre(clienteDto.getNombre());
        cliente.setTelefono(clienteDto.getTelefono());
        cliente.setCorreo(clienteDto.getCorreo());
        cliente.setDireccion(clienteDto.getDireccion());

        Clientes clienteGuardado = clienteService.guardarCliente(cliente);

        return ResponseEntity.ok(clienteGuardado);
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Ventas> postMethodName(@RequestBody VentasDto ventaDto, @AuthenticationPrincipal Usuarios userAtenticado) {

        if(userAtenticado==null){
            // Si el usuario está autenticado, podemos continuar
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null); // Redirigir a la página de inicio de sesión
        }

        logger.info("Datos recibidos: {}", ventaDto);

        //obtener el cliente
        Clientes cliente = clienteService.buscarPorId(ventaDto.getCliente_id().getId());
        logger.info("Cliente encontrado: {}", cliente);

        //obtener al usuario
        
        

        //obtenemos la fecha actual
        Date fechaActual = new Date();

        //obtenemos el total de la venta
        double totalVenta = ventaDto.getTotal();

        //cramos la venta
        Ventas ventas = new Ventas();
        ventas.setCliente_id(cliente);
        ventas.setUsuario_id(userAtenticado);
        ventas.setTotal(totalVenta);
        ventas.setFecha(fechaActual);

        //guardamos la venta
        Ventas ventaGuardada = ventasServices.guardarVenta(ventas);
        logger.info("Ventas exitosa");
        

        ventaDto.getDetalles().stream().forEach(det -> {
            
            //creamos la variable detalle ventas
            DetalleVentas detalles = new DetalleVentas();
            //obtenemos el producto
            Productos producto = productoService.obtenerProductoPorId(det.getProducto_id().getId()).orElseThrow(()-> new RuntimeException("Producto no encontrado"));
            int stockAnterior = producto.getCantidad();
            producto.setCantidad(stockAnterior - det.getCantidad());
            productoService.guardarProducto(producto);
            // avisa solo cuando la venta hace que el producto cruce el minimo (no en cada venta posterior)
            if (stockAnterior > STOCK_MINIMO && producto.getCantidad() <= STOCK_MINIMO) {
                eventos.publishEvent(new EventosNegocio.StockBajo(producto.getNombre(), producto.getCantidad(), producto.getUnidad()));
            }
            detalles.setCantidad(det.getCantidad());
            detalles.setSubtotal(det.getSubtotal());
            detalles.setProducto_id(producto);
            detalles.setVenta_id(ventaGuardada);

            DetalleVentas DetalleGuardado = detalleVentaService.guardar(detalles);
            logger.info("Detalle guardado: {}", DetalleGuardado);

        });

        return ResponseEntity.ok(ventas);
    }

}
