document.addEventListener("DOMContentLoaded", function () {
    let productosAgregados = [];
    let clienteActual = null;
    let clientes = [];
    let productos = [];
    let totalVenta = 0;

    const categoriaSelect = document.getElementById("categoria");
    const productoSelect = document.getElementById("producto");
    const precioInput = document.getElementById("precio");
    const cantidadInput = document.getElementById("cantidad");
    const agregarBtn = document.getElementById("agregarProducto");
    const detallesTable = document.getElementById("detallesVenta");
    const totalVentaSpan = document.getElementById("totalVenta");
    const clienteForm = document.getElementById("clienteForm");
    const dniInput = document.getElementById("dni");
    const buscarClienteBtn = document.getElementById("buscarCliente");
    const nombreClienteInput = document.getElementById("nombreCliente");
    const celularInput = document.getElementById("celular");
    const correoInput = document.getElementById("correo");
    const direccionInput = document.getElementById("direccion");
    const procesarVentaBtn = document.getElementById("procesarVenta");
    const cancelarVentaBtn = document.getElementById("cancelarVenta");
    const confirmarVentaBtn = document.getElementById("confirmarVenta");
    const modalElement = document.getElementById("confirmacionVentaModal");
    const confirmacionModal = bootstrap.Modal.getOrCreateInstance(modalElement);
    const avisoVentaModal = bootstrap.Modal.getOrCreateInstance(
        document.getElementById("avisoVentaModal")
    );

    clienteForm.addEventListener("submit", event => event.preventDefault());
    categoriaSelect.addEventListener("change", cargarProductosPorCategoria);
    productoSelect.addEventListener("change", actualizarPrecio);
    agregarBtn.addEventListener("click", agregarProductoAVenta);
    buscarClienteBtn.addEventListener("click", buscarClientePorDNI);
    procesarVentaBtn.addEventListener("click", prepararConfirmacion);
    cancelarVentaBtn.addEventListener("click", limpiarFormulario);
    confirmarVentaBtn.addEventListener("click", confirmarYGuardarVenta);
    document.getElementById("nuevaVenta").addEventListener("click", limpiarFormulario);
    detallesTable.addEventListener("click", manejarAccionProducto);
    dniInput.addEventListener("input", function () {
        clienteActual = null;
    });
    [nombreClienteInput, celularInput, correoInput, direccionInput].forEach(input => {
        input.addEventListener("input", function () {
            clienteActual = null;
        });
    });

    cargarDatosVentas().catch(error => {
        console.error("No se pudieron cargar los datos para registrar ventas:", error);
        alert("No se pudieron cargar los productos y clientes. Recargue la página e inténtelo de nuevo.");
    });

    async function cargarDatosVentas() {
        const response = await fetch("/venta/datosventas");
        if (!response.ok) {
            throw new Error(`Error HTTP ${response.status}`);
        }
        const data = await response.json();
        clientes = data.clientes || [];
        productos = data.productos || [];
    }

    function cargarProductosPorCategoria() {
        const categoriaId = Number(categoriaSelect.value);
        productoSelect.replaceChildren(new Option("-- Seleccione --", ""));

        if (!categoriaId) {
            precioInput.value = "";
            return;
        }

        productos.filter(producto => producto.categoria_id?.id === categoriaId).forEach(producto => {
            const option = new Option(
                `${producto.nombre} - S/${Number(producto.precio).toFixed(2)}`,
                producto.id
            );
            option.dataset.precio = producto.precio;
            productoSelect.add(option);
        });
        precioInput.value = "";
    }

    function actualizarPrecio() {
        const option = productoSelect.options[productoSelect.selectedIndex];
        precioInput.value = option?.dataset.precio
            ? Number(option.dataset.precio).toFixed(2)
            : "";
    }

    function agregarProductoAVenta() {
        const productoId = Number(productoSelect.value);
        const cantidad = Number.parseInt(cantidadInput.value, 10);
        const producto = productos.find(item => item.id === productoId);
        const precio = Number(precioInput.value);

        if (!producto || !Number.isInteger(cantidad) || cantidad < 1 || !Number.isFinite(precio)) {
            alert("Seleccione un producto e indique una cantidad válida.");
            return;
        }
        if (!validarStock(producto, cantidad)) {
            return;
        }

        let productoExistente = productosAgregados.find(item => item.id === productoId);
        if (productoExistente) {
            productoExistente.cantidad += cantidad;
            productoExistente.subtotal = productoExistente.cantidad * productoExistente.precio;
        } else {
            productoExistente = {
                id: productoId,
                nombre: producto.nombre,
                precio,
                cantidad,
                subtotal: precio * cantidad
            };
            productosAgregados.push(productoExistente);
        }

        producto.cantidad -= cantidad;
        actualizarTablaProductos();
        calcularTotal();
        productoSelect.selectedIndex = 0;
        precioInput.value = "";
        cantidadInput.value = "1";
        categoriaSelect.focus();
    }

    function validarStock(producto, cantidad) {
        if (producto.cantidad < cantidad) {
            alert(`Stock insuficiente para ${producto.nombre}. Disponible: ${producto.cantidad}`);
            return false;
        }
        return true;
    }

    function actualizarTablaProductos() {
        detallesTable.replaceChildren();
        productosAgregados.forEach((producto, index) => {
            const row = document.createElement("tr");
            appendCell(row, producto.nombre);
            appendCell(row, `S/${producto.precio.toFixed(2)}`, "text-end");
            appendCell(row, String(producto.cantidad), "text-center");
            appendCell(row, `S/${producto.subtotal.toFixed(2)}`, "text-end");

            const acciones = document.createElement("td");
            acciones.className = "text-center";
            [
                ["+", "btn-success", "aumentar"],
                ["−", "btn-warning", "disminuir"],
                ["Eliminar", "btn-danger", "eliminar"]
            ].forEach(([texto, estilo, accion]) => {
                const button = document.createElement("button");
                button.type = "button";
                button.className = `btn btn-sm ${estilo} me-1`;
                button.dataset.accion = accion;
                button.dataset.index = String(index);
                button.textContent = texto;
                acciones.appendChild(button);
            });
            row.appendChild(acciones);
            detallesTable.appendChild(row);
        });
    }

    function appendCell(row, value, className = "") {
        const cell = document.createElement("td");
        cell.className = className;
        cell.textContent = value;
        row.appendChild(cell);
    }

    function manejarAccionProducto(event) {
        const button = event.target.closest("button[data-accion]");
        if (!button) {
            return;
        }

        const index = Number(button.dataset.index);
        const item = productosAgregados[index];
        if (!item) {
            return;
        }

        const producto = productos.find(candidate => candidate.id === item.id);
        if (button.dataset.accion === "aumentar") {
            if (!validarStock(producto, 1)) {
                return;
            }
            producto.cantidad -= 1;
            item.cantidad += 1;
        } else if (button.dataset.accion === "disminuir" && item.cantidad > 1) {
            producto.cantidad += 1;
            item.cantidad -= 1;
        } else {
            producto.cantidad += item.cantidad;
            productosAgregados.splice(index, 1);
        }

        item.subtotal = item.precio * item.cantidad;
        actualizarTablaProductos();
        calcularTotal();
    }

    function calcularTotal() {
        totalVenta = productosAgregados.reduce((sum, producto) => sum + producto.subtotal, 0);
        totalVentaSpan.textContent = `S/${totalVenta.toFixed(2)}`;
    }

    function buscarClientePorDNI() {
        const dni = dniInput.value.trim();
        if (!/^\d{8}$/.test(dni)) {
            alert("Ingrese un DNI válido de 8 dígitos.");
            return;
        }

        const encontrado = clientes.find(cliente => cliente.dni === dni);
        if (!encontrado) {
            clienteActual = null;
            nombreClienteInput.value = "";
            celularInput.value = "";
            correoInput.value = "";
            direccionInput.value = "";
            nombreClienteInput.focus();
            alert("Cliente no encontrado. Complete sus datos para registrarlo con la venta.");
            return;
        }

        clienteActual = encontrado;
        nombreClienteInput.value = encontrado.nombre || "";
        celularInput.value = encontrado.telefono || "";
        correoInput.value = encontrado.correo || "";
        direccionInput.value = encontrado.direccion || "";
    }

    function prepararConfirmacion() {
        if (!clienteForm.reportValidity()) {
            return;
        }
        if (productosAgregados.length === 0) {
            avisoVentaModal.show();
            return;
        }

        document.getElementById("resumenCliente").textContent =
            `${nombreClienteInput.value.trim()} · DNI ${dniInput.value.trim()}`;
        document.getElementById("resumenProductos").replaceChildren();
        productosAgregados.forEach(producto => {
            const row = document.createElement("tr");
            appendCell(row, producto.nombre);
            appendCell(row, `S/${producto.precio.toFixed(2)}`, "text-end");
            appendCell(row, String(producto.cantidad), "text-center");
            appendCell(row, `S/${producto.subtotal.toFixed(2)}`, "text-end");
            document.getElementById("resumenProductos").appendChild(row);
        });
        document.getElementById("resumenTotal").textContent = `S/${totalVenta.toFixed(2)}`;
        document.getElementById("resumenConfirmacion").hidden = false;
        document.getElementById("resultadoConfirmacion").hidden = true;
        document.getElementById("errorVenta").hidden = true;
        document.getElementById("cancelarConfirmacion").hidden = false;
        document.getElementById("confirmarVenta").hidden = false;
        document.getElementById("nuevaVenta").hidden = true;
        document.getElementById("verReporteVenta").hidden = true;
        confirmacionModal.show();
    }

    async function confirmarYGuardarVenta() {
        confirmarVentaBtn.disabled = true;
        confirmarVentaBtn.textContent = "Guardando venta...";
        document.getElementById("errorVenta").hidden = true;

        try {
            const dni = dniInput.value.trim();
            const clienteExistente = clienteActual && clienteActual.dni === dni
                ? clienteActual
                : clientes.find(cliente => cliente.dni === dni);
            const venta = {
                cliente_id: clienteExistente ? { id: clienteExistente.id } : null,
                cliente: clienteExistente ? null : {
                    dni,
                    nombre: nombreClienteInput.value.trim(),
                    telefono: celularInput.value.trim(),
                    correo: correoInput.value.trim(),
                    direccion: direccionInput.value.trim()
                },
                detalles: productosAgregados.map(producto => ({
                    producto_id: { id: producto.id },
                    cantidad: producto.cantidad,
                    subtotal: producto.subtotal
                })),
                total: totalVenta
            };

            const response = await fetch("/venta", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(venta)
            });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.detail || body.message || `Error HTTP ${response.status}`);
            }

            const ventaGuardada = await response.json();
            document.getElementById("resumenConfirmacion").hidden = true;
            document.getElementById("resultadoConfirmacion").hidden = false;
            document.getElementById("mensajeVentaRegistrada").textContent =
                `Venta #${ventaGuardada.id} registrada correctamente por S/${Number(ventaGuardada.total).toFixed(2)}.`;
            document.getElementById("cancelarConfirmacion").hidden = true;
            confirmarVentaBtn.hidden = true;
            document.getElementById("nuevaVenta").hidden = false;
            const rol = document.getElementById("rolUsuario")?.textContent.trim();
            document.getElementById("verReporteVenta").hidden = rol !== "ADMIN";

            await cargarDatosVentas().catch(error => {
                console.error("La venta se guardó, pero no se pudo actualizar el inventario mostrado:", error);
            });
        } catch (error) {
            console.error("No se pudo registrar la venta:", error);
            const errorContainer = document.getElementById("errorVenta");
            errorContainer.querySelector(".alert").textContent =
                `No se pudo registrar la venta: ${error.message}`;
            errorContainer.hidden = false;
        } finally {
            confirmarVentaBtn.disabled = false;
            confirmarVentaBtn.innerHTML = '<i class="fas fa-check me-2"></i>Confirmar y registrar';
        }
    }

    function limpiarFormulario() {
        categoriaSelect.selectedIndex = 0;
        productoSelect.replaceChildren(new Option("-- Seleccione --", ""));
        precioInput.value = "";
        cantidadInput.value = "1";
        productosAgregados = [];
        detallesTable.replaceChildren();
        totalVenta = 0;
        totalVentaSpan.textContent = "S/ 0.00";
        clienteForm.reset();
        clienteActual = null;
        categoriaSelect.focus();
    }
});
