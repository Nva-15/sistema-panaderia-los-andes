document.addEventListener('DOMContentLoaded', function() {
    // Elementos del DOM
    const form = document.getElementById('reporteForm');
    const btnGenerar = document.getElementById('btnGenerar');
    const btnLimpiar = document.getElementById('btnLimpiar');
    const btnExportar = document.getElementById('btnExportar');
    const loading = document.getElementById('loading');
    const resultados = document.getElementById('resultados');
    const tablaBody = document.querySelector('#tablaResultados tbody');
    
    // Variables para almacenar datos
    let ventasData = [];
    let opcionesFiltros = {};
    let filtrosActuales = {};
    
    // Mostrar ventas recientes sin exigir que el usuario pulse "Generar".
    configurarFechasPredeterminadas();
    cargarOpcionesFiltros().then(generarReporte);
    
    // Evento para generar reporte
    form.addEventListener('submit', function(e) {
        e.preventDefault();
        generarReporte();
    });
    
    // Evento para limpiar filtros
    btnLimpiar.addEventListener('click', function() {
        form.reset();
        resultados.style.display = 'none';
        // Restablecer fechas a los últimos 30 días al limpiar
        configurarFechasPredeterminadas();
        generarReporte();
    });
    
    // Evento para exportar datos
    btnExportar.addEventListener('click', exportarCSV);
    
    // Función para configurar fechas predeterminadas (últimos 30 días)
    function configurarFechasPredeterminadas() {
        const fechaHasta = new Date();
        const fechaDesde = new Date();
        fechaDesde.setDate(fechaHasta.getDate() - 30);
        
        // Formatear fechas como YYYY-MM-DD (formato de input date)
        const formatDate = (date) => {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        };
        
        document.getElementById('fechaDesde').value = formatDate(fechaDesde);
        document.getElementById('fechaHasta').value = formatDate(fechaHasta);
        
        // Guardar en filtros actuales
        filtrosActuales.fechaDesde = formatDate(fechaDesde);
        filtrosActuales.fechaHasta = formatDate(fechaHasta);
    }
    
    // Función para cargar opciones de filtros
    async function cargarOpcionesFiltros() {
        try {
            const response = await fetch('/api/reporte-ventas/filtros');
            if (!response.ok) {
                throw new Error(`Error HTTP: ${response.status}`);
            }
            const data = await response.json();
            
            if (data.error) {
                throw new Error(data.error);
            }
            
            opcionesFiltros = data;
            llenarSelectsFiltros();
        } catch (error) {
            console.error('Error al cargar filtros:', error);
            mostrarError('Error al cargar opciones de filtros: ' + error.message);
        }
    }
    
    // Llenar selects con opciones de filtros
    function llenarSelectsFiltros() {
        const selectCategoria = document.getElementById('categoria');
        const selectUsuario = document.getElementById('usuario');
        
        // Limpiar selects primero
        selectCategoria.innerHTML = '<option value="">Todas</option>';
        selectUsuario.innerHTML = '<option value="">Todos</option>';
        
        if (opcionesFiltros.categorias) {
            opcionesFiltros.categorias.forEach(cat => {
                const option = document.createElement('option');
                option.value = cat.id;
                option.textContent = cat.nombre;
                selectCategoria.appendChild(option);
            });
        }
        
        if (opcionesFiltros.usuarios) {
            opcionesFiltros.usuarios.forEach(user => {
                const option = document.createElement('option');
                option.value = user.id;
                option.textContent = user.nombre;
                selectUsuario.appendChild(option);
            });
        }
    }
    
    // Función principal para generar el reporte
    async function generarReporte() {
        const fechaDesde = document.getElementById('fechaDesde').value;
        const fechaHasta = document.getElementById('fechaHasta').value;
        const categoria = document.getElementById('categoria').value || null;
        const usuario = document.getElementById('usuario').value || null;
        const agruparPor = document.getElementById('agruparPor').value || null;
        
        if (!fechaDesde || !fechaHasta) {
            mostrarError('Debe seleccionar un rango de fechas');
            return;
        }
        
        // Validar que fechaDesde no sea mayor que fechaHasta
        if (new Date(fechaDesde) > new Date(fechaHasta)) {
            mostrarError('La fecha "Desde" no puede ser mayor que la fecha "Hasta"');
            return;
        }
        
        // Guardar filtros actuales para exportación
        filtrosActuales = { fechaDesde, fechaHasta, categoria, usuario };
        
        try {
            loading.style.display = 'block';
            resultados.style.display = 'none';
            
            const params = new URLSearchParams();
            params.append('fechaDesde', fechaDesde);
            params.append('fechaHasta', fechaHasta);
            if (categoria) params.append('categoriaId', categoria);
            if (usuario) params.append('usuarioId', usuario);
            
            const response = await fetch(`/api/reporte-ventas/ventas?${params.toString()}`);
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `Error HTTP: ${response.status}`);
            }
            
            ventasData = await response.json();
            
            if (ventasData.length === 0) {
                mostrarError('No se encontraron ventas con los filtros seleccionados');
                return;
            }
            
            mostrarResultados(ventasData, agruparPor);
            
        } catch (error) {
            console.error('Error:', error);
            mostrarError(error.message || 'Error al generar el reporte');
        } finally {
            loading.style.display = 'none';
        }
    }
    
    // Mostrar resultados en la tabla
    function mostrarResultados(data, agruparPor) {
        tablaBody.innerHTML = '';
        
        // Agrupar datos si es necesario
        if (agruparPor) {
            data = agruparDatos(data, agruparPor);
        }
        
        let total = 0;
        
        data.forEach(item => {
            const row = document.createElement('tr');
            const fechaFormateada = item[1]
                ? new Intl.DateTimeFormat('es-PE', {
                    dateStyle: 'short',
                    timeStyle: 'short',
                    timeZone: 'America/Lima'
                }).format(new Date(item[1]))
                : '—';
            const valores = [
                item[0] || '—',
                fechaFormateada,
                item[2] || 'N/A',
                item[3] || 'N/A',
                item[4] || '—',
                item[5] || '—',
                item[6] || '—',
                item[7] ?? 0,
                item[8] == null ? '—' : `S/ ${parseFloat(item[8]).toFixed(2)}`,
                `S/ ${parseFloat(item[9]).toFixed(2)}`
            ];
            valores.forEach((valor, index) => {
                const cell = document.createElement('td');
                cell.textContent = valor;
                if (index >= 8) cell.className = 'text-end';
                row.appendChild(cell);
            });
            
            tablaBody.appendChild(row);
            total += parseFloat(item[9]);
        });
        
        // Agregar fila de total
        const totalRow = document.createElement('tr');
        totalRow.className = 'table-active';
        const totalLabel = document.createElement('td');
        totalLabel.colSpan = 9;
        totalLabel.className = 'text-end';
        const label = document.createElement('strong');
        label.textContent = 'Total:';
        totalLabel.appendChild(label);
        const totalValue = document.createElement('td');
        totalValue.className = 'text-end';
        const amount = document.createElement('strong');
        amount.textContent = `S/ ${total.toFixed(2)}`;
        totalValue.appendChild(amount);
        totalRow.append(totalLabel, totalValue);
        tablaBody.appendChild(totalRow);
        
        resultados.style.display = 'block';
    }
    
    // Función para agrupar datos
    function agruparDatos(data, criterio) {
        const grupos = {};
        const indexMap = {
            'categoria': 6,
            'usuario': 2
        };
        
        const index = indexMap[criterio];
        
        data.forEach(item => {
            const key = item[index] || 'Sin ' + criterio;
            if (!grupos[key]) {
                grupos[key] = {
                    cantidad: 0,
                    subtotal: 0
                };
            }
            grupos[key].cantidad += Number(item[7]);
            grupos[key].subtotal += Number(item[9]);
        });
        
        // Convertir a array para mostrar
        return Object.keys(grupos).map(key => {
            const grupo = grupos[key];
            return [
                '',
                '',
                criterio === 'usuario' ? key : '—',
                '—',
                '—',
                '—',
                criterio === 'categoria' ? key : '—',
                grupo.cantidad,
                null,
                grupo.subtotal
            ];
        });
    }
    
    // Exportar a CSV
    async function exportarCSV() {
        if (ventasData.length === 0) {
            mostrarError('No hay datos para exportar');
            return;
        }
        
        try {
            const { fechaDesde, fechaHasta, categoria, usuario } = filtrosActuales;
            
            const params = new URLSearchParams();
            params.append('fechaDesde', fechaDesde);
            params.append('fechaHasta', fechaHasta);
            if (categoria) params.append('categoriaId', categoria);
            if (usuario) params.append('usuarioId', usuario);
            
            const response = await fetch(`/api/reporte-ventas/exportar?${params.toString()}`);
            
            if (!response.ok) {
                throw new Error('Error al exportar datos');
            }
            
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `reporte_ventas_${new Date().toISOString().slice(0,10)}.csv`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            
        } catch (error) {
            console.error('Error al exportar:', error);
            mostrarError('Error al exportar el reporte');
        }
    }
    
    // Mostrar mensaje de error
    function mostrarError(mensaje) {
        const alertContainer = document.getElementById('alertContainer');
        alertContainer.innerHTML = '';
        
        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-danger alert-dismissible fade show';
        const icono = document.createElement('i');
        icono.className = 'fas fa-exclamation-circle me-2';
        const cerrar = document.createElement('button');
        cerrar.type = 'button';
        cerrar.className = 'btn-close';
        cerrar.dataset.bsDismiss = 'alert';
        cerrar.setAttribute('aria-label', 'Cerrar');
        errorDiv.append(icono, document.createTextNode(mensaje), cerrar);
        
        alertContainer.appendChild(errorDiv);
        
        // Eliminar el mensaje después de 5 segundos
        setTimeout(() => {
            errorDiv.remove();
        }, 5000);
    }
});