package com.proyecto.panaderialosandes.controllers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.proyecto.panaderialosandes.dto.ClienteDto;
import com.proyecto.panaderialosandes.dto.DetalleVentaDto;
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
        validarDatosCliente(clienteDto);
        return ResponseEntity.ok(guardarOActualizarCliente(clienteDto));
    }

    @PostMapping
    @ResponseBody
    @Transactional
    public ResponseEntity<Ventas> registrarVenta(@RequestBody VentasDto ventaDto,
            @AuthenticationPrincipal Usuarios userAtenticado) {
        if (userAtenticado == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (ventaDto == null || ventaDto.getDetalles() == null || ventaDto.getDetalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta debe incluir al menos un producto");
        }

        Map<Integer, Integer> cantidadesPorProducto = new TreeMap<>();
        for (DetalleVentaDto detalle : ventaDto.getDetalles()) {
            if (detalle == null || detalle.getProducto_id() == null || detalle.getProducto_id().getId() <= 0
                    || detalle.getCantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto y cantidad deben ser válidos");
            }
            int productoId = detalle.getProducto_id().getId();
            int cantidadActual = cantidadesPorProducto.getOrDefault(productoId, 0);
            if (cantidadActual > Integer.MAX_VALUE - detalle.getCantidad()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada no es válida");
            }
            cantidadesPorProducto.put(productoId, cantidadActual + detalle.getCantidad());
        }

        Map<Productos, Integer> productosVendidos = new java.util.LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> item : cantidadesPorProducto.entrySet()) {
            Productos producto = productoService.obtenerProductoParaVenta(item.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            int cantidad = item.getValue();
            if ("inactivo".equalsIgnoreCase(producto.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto " + producto.getNombre() + " está inactivo");
            }
            if (producto.getCantidad() < cantidad) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para " + producto.getNombre() + ". Disponible: " + producto.getCantidad());
            }
            if (!Double.isFinite(producto.getPrecio()) || producto.getPrecio() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El precio de " + producto.getNombre() + " no es válido");
            }

            BigDecimal precio = BigDecimal.valueOf(producto.getPrecio()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cantidad));
            total = total.add(subtotal);
            productosVendidos.put(producto, cantidad);
        }

        Clientes cliente = resolverCliente(ventaDto);
        Ventas venta = new Ventas();
        venta.setCliente_id(cliente);
        venta.setUsuario_id(userAtenticado);
        venta.setFecha(new Date());
        venta.setTotal(total.setScale(2, RoundingMode.HALF_UP).doubleValue());
        Ventas ventaGuardada = ventasServices.guardarVenta(venta);

        for (Map.Entry<Productos, Integer> item : productosVendidos.entrySet()) {
            Productos producto = item.getKey();
            int stockAnterior = producto.getCantidad();
            int cantidad = item.getValue();
            BigDecimal precio = BigDecimal.valueOf(producto.getPrecio()).setScale(2, RoundingMode.HALF_UP);
            producto.setCantidad(stockAnterior - cantidad);
            productoService.guardarProducto(producto);

            DetalleVentas detalle = new DetalleVentas();
            detalle.setCantidad(cantidad);
            detalle.setSubtotal(precio.multiply(BigDecimal.valueOf(cantidad)).doubleValue());
            detalle.setProducto_id(producto);
            detalle.setVenta_id(ventaGuardada);
            detalleVentaService.guardar(detalle);

            if (stockAnterior > STOCK_MINIMO && producto.getCantidad() <= STOCK_MINIMO) {
                eventos.publishEvent(new EventosNegocio.StockBajo(
                        producto.getNombre(), producto.getCantidad(), producto.getUnidad()));
            }
        }

        logger.info("Venta {} registrada por {} con total {}", ventaGuardada.getId(),
                userAtenticado.getUsername(), ventaGuardada.getTotal());
        return ResponseEntity.ok(ventaGuardada);
    }

    private Clientes resolverCliente(VentasDto ventaDto) {
        if (ventaDto.getCliente_id() != null && ventaDto.getCliente_id().getId() > 0) {
            Clientes cliente = clienteService.buscarPorId(ventaDto.getCliente_id().getId());
            if (cliente == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
            }
            return cliente;
        }

        validarDatosCliente(ventaDto.getCliente());
        Clientes cliente = clienteService.buscarClientePorDni(ventaDto.getCliente().getDni());
        if (cliente == null) {
            cliente = new Clientes();
        }
        cliente.setDni(ventaDto.getCliente().getDni().trim());
        cliente.setNombre(ventaDto.getCliente().getNombre().trim());
        cliente.setTelefono(ventaDto.getCliente().getTelefono().trim());
        cliente.setCorreo(ventaDto.getCliente().getCorreo().trim());
        cliente.setDireccion(ventaDto.getCliente().getDireccion().trim());
        return clienteService.guardarCliente(cliente);
    }

    private Clientes guardarOActualizarCliente(ClienteDto datos) {
        Clientes cliente = clienteService.buscarClientePorDni(datos.getDni().trim());
        if (cliente == null) {
            cliente = new Clientes();
        }
        cliente.setDni(datos.getDni().trim());
        cliente.setNombre(datos.getNombre().trim());
        cliente.setTelefono(datos.getTelefono().trim());
        cliente.setCorreo(datos.getCorreo().trim());
        cliente.setDireccion(datos.getDireccion().trim());
        return clienteService.guardarCliente(cliente);
    }

    private void validarDatosCliente(ClienteDto cliente) {
        if (cliente == null || cliente.getDni() == null || !cliente.getDni().trim().matches("\\d{8}")
                || cliente.getNombre() == null || cliente.getNombre().isBlank()
                || cliente.getTelefono() == null || !cliente.getTelefono().trim().matches("\\d{9}")
                || cliente.getCorreo() == null || !cliente.getCorreo().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || cliente.getDireccion() == null || cliente.getDireccion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos del cliente no son válidos");
        }
    }

}
