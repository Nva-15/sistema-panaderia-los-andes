document.addEventListener('DOMContentLoaded', function () {
    const visor = document.getElementById('visor');
    const lista = document.getElementById('lista');
    const filtros = document.getElementById('filtros');
    const sinProductos = document.getElementById('sinProductos');
    const nombreProducto = document.getElementById('nombreProducto');
    const categoriaProducto = document.getElementById('categoriaProducto');
    const precioProducto = document.getElementById('precioProducto');
    const disponibilidadProducto = document.getElementById('disponibilidadProducto');
    const textoAr = document.getElementById('textoAr');

    const iconos = { torta: 'fa-cake-candles', pan: 'fa-bread-slice', pastel: 'fa-ice-cream' };
    const etiquetas = {
        disponible: { texto: 'Disponible', clase: 'bg-success' },
        pocas: { texto: 'Últimas unidades', clase: 'bg-warning text-dark' },
        agotado: { texto: 'Agotado hoy', clase: 'bg-secondary' }
    };

    let productos = [];
    let categoriaActiva = '';
    let seleccionado = null;

    // si el celular admite Realidad Aumentada se cambia el texto de ayuda
    visor.addEventListener('load', () => {
        if (visor.canActivateAR) {
            textoAr.innerHTML = 'Toca <strong>Ver en tu mesa</strong> y apunta la cámara a una superficie plana.';
        }
    });

    cargar();
    // los precios y la disponibilidad se refrescan cada minuto
    setInterval(cargar, 60000);

    async function cargar() {
        try {
            const response = await fetch('/api/catalogo');
            productos = await response.json();
            mostrarFiltros();
            mostrarLista();
            if (productos.length === 0) {
                nombreProducto.textContent = 'Sin productos';
                return;
            }
            // se mantiene el producto elegido; si ya no existe, se elige el primero
            const actual = seleccionado && productos.find(p => p.id === seleccionado.id);
            seleccionar(actual || productos[0]);
        } catch (error) {
            console.error('Error al cargar el catálogo:', error);
            nombreProducto.textContent = 'No se pudo cargar el catálogo';
        }
    }

    function tipo(producto) {
        if (producto.modelo.includes('torta')) return 'torta';
        if (producto.modelo.includes('pan')) return 'pan';
        return 'pastel';
    }

    function mostrarFiltros() {
        const categorias = [...new Set(productos.map(p => p.categoria).filter(c => c))];
        filtros.querySelectorAll('[data-categoria]:not([data-categoria=""])').forEach(b => b.remove());
        categorias.forEach(c => {
            const boton = document.createElement('button');
            boton.className = 'btn btn-sm btn-filtro' + (c === categoriaActiva ? ' activo' : '');
            boton.dataset.categoria = c;
            boton.textContent = c;
            filtros.appendChild(boton);
        });
        filtros.querySelectorAll('[data-categoria]').forEach(b => {
            b.classList.toggle('activo', b.dataset.categoria === categoriaActiva);
            b.onclick = () => {
                categoriaActiva = b.dataset.categoria;
                mostrarFiltros();
                mostrarLista();
            };
        });
    }

    function mostrarLista() {
        const visibles = productos.filter(p => !categoriaActiva || p.categoria === categoriaActiva);
        sinProductos.hidden = visibles.length > 0;
        lista.innerHTML = '';
        visibles.forEach(p => {
            const col = document.createElement('div');
            col.className = 'col-6 col-md-4 col-lg-3';
            const tarjeta = document.createElement('button');
            tarjeta.type = 'button';
            tarjeta.className = 'tarjeta-producto w-100' + (seleccionado && seleccionado.id === p.id ? ' elegido' : '');
            tarjeta.innerHTML = `<i class="fas ${iconos[tipo(p)]} icono-producto"></i>
                <span class="nombre"></span><span class="precio-lista"></span><span class="badge"></span>`;
            tarjeta.querySelector('.nombre').textContent = p.nombre;
            tarjeta.querySelector('.precio-lista').textContent = 'S/ ' + Number(p.precio).toFixed(2);
            const etiqueta = etiquetas[p.disponibilidad];
            const badge = tarjeta.querySelector('.badge');
            badge.className = 'badge ' + etiqueta.clase;
            badge.textContent = etiqueta.texto;
            tarjeta.addEventListener('click', () => {
                seleccionar(p);
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
            col.appendChild(tarjeta);
            lista.appendChild(col);
        });
    }

    function seleccionar(p) {
        seleccionado = p;
        if (visor.getAttribute('src') !== p.modelo) {
            visor.setAttribute('src', p.modelo);
        }
        visor.setAttribute('alt', 'Modelo 3D de ' + p.nombre);
        nombreProducto.textContent = p.nombre;
        categoriaProducto.textContent = p.categoria + (p.unidad ? ' · por ' + p.unidad : '');
        precioProducto.textContent = 'S/ ' + Number(p.precio).toFixed(2);
        const etiqueta = etiquetas[p.disponibilidad];
        disponibilidadProducto.className = 'badge ' + etiqueta.clase;
        disponibilidadProducto.textContent = etiqueta.texto;
        mostrarLista();
    }
});
