document.addEventListener('DOMContentLoaded', function () {
    let idProducto = null;
    const nombreProducto = document.getElementById('nombreEditar');
    const stockProducto = document.getElementById('stockEditar');
    const precioProducto = document.getElementById('precioEditar');
    const unidadProducto = document.getElementById('unidadEditar');
    const categoriaProducto = document.getElementById('categoriaEditar');
    const formulario = document.getElementById('formEditarProducto');
    const botonGuardar = document.getElementById('btnGuardarProducto');
    const error = document.getElementById('errorEdicionProducto');

    document.querySelectorAll('.botonEditar').forEach(function (boton) {
        boton.addEventListener('click', function () {
            idProducto = boton.dataset.id;
            nombreProducto.value = boton.dataset.nombre || '';
            stockProducto.value = boton.dataset.cantidad || '';
            precioProducto.value = boton.dataset.precio || '';
            unidadProducto.value = boton.dataset.unidad || '';
            categoriaProducto.value = boton.dataset.categoriaId || '';
            error.hidden = true;
            error.textContent = '';
        });
    });

    formulario.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (!idProducto || !formulario.reportValidity()) {
            return;
        }

        botonGuardar.disabled = true;
        error.hidden = true;
        try {
            const response = await fetch('/productos/actualizar/' + encodeURIComponent(idProducto), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    nombre: nombreProducto.value.trim(),
                    cantidad: Number(stockProducto.value),
                    precio: Number(precioProducto.value),
                    unidad: unidadProducto.value.trim(),
                    categoriaId: Number(categoriaProducto.value)
                })
            });

            if (!response.ok) {
                const detalle = await response.text();
                throw new Error(detalle || 'No se pudo actualizar el producto.');
            }

            window.location.reload();
        } catch (e) {
            error.textContent = e.message || 'No se pudo actualizar el producto.';
            error.hidden = false;
        } finally {
            botonGuardar.disabled = false;
        }
    });
});
