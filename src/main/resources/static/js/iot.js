document.addEventListener('DOMContentLoaded', function () {
    const tarjetas = document.getElementById('tarjetas');
    const graficos = document.getElementById('graficos');
    const tablaAlertas = document.getElementById('tablaAlertas');
    const selectHoras = document.getElementById('horas');
    const estadoDispositivo = document.getElementById('estadoDispositivo');
    const ultimaLectura = document.getElementById('ultimaLectura');
    const contadorAlertas = document.getElementById('contadorAlertas');

    const estadoMqtt = document.getElementById('estadoMqtt');
    const iconos = {
        vitrina_temp_c: 'fa-temperature-half',
        vitrina_hum_pct: 'fa-droplet',
        bandeja_peso_g: 'fa-weight-hanging',
        bandeja_unidades: 'fa-bread-slice',
        puerta_abierta: 'fa-door-open',
        clientes_acumulados: 'fa-person-walking'
    };
    const coloresPrioridad = { ALTA: 'bg-danger', MEDIA: 'bg-warning text-dark', BAJA: 'bg-info text-dark' };
    const charts = {};

    selectHoras.addEventListener('change', actualizar);
    actualizar();
    setInterval(actualizar, 10000);

    async function actualizar() {
        try {
            const response = await fetch('/api/iot/estado?horas=' + selectHoras.value);
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const datos = await response.json();
            mostrarEstado(datos);
            mostrarTarjetas(datos.variables);
            mostrarGraficos(datos.variables);
            mostrarAlertas(datos.alertas);
        } catch (error) {
            console.error('Error al cargar el estado IoT:', error);
        }
    }

    function mostrarEstado(datos) {
        estadoMqtt.className = 'badge ' + (datos.mqttConectado ? 'bg-success' : 'bg-secondary');
        estadoMqtt.textContent = datos.mqttConectado ? 'MQTT conectado' : 'MQTT sin conexión';
        ultimaLectura.textContent = datos.ultimaLectura || 'sin lecturas';
        if (datos.ultimaLectura == null) {
            estadoDispositivo.className = 'badge bg-secondary';
            estadoDispositivo.textContent = 'Sin datos';
        } else if (datos.enLinea) {
            estadoDispositivo.className = 'badge bg-success';
            estadoDispositivo.innerHTML = '<i class="fas fa-wifi me-1"></i>ESP32 en línea';
        } else {
            estadoDispositivo.className = 'badge bg-danger';
            estadoDispositivo.innerHTML = '<i class="fas fa-plug-circle-xmark me-1"></i>ESP32 desconectado';
        }
    }

    function rango(v) {
        if (v.minimo == null && v.maximo == null) return v.descripcion;
        if (v.maximo == null) return `mínimo ${v.minimo} ${v.unidad}`;
        if (v.minimo == null) return `máximo ${v.maximo} ${v.unidad}`;
        return `${v.minimo} – ${v.maximo} ${v.unidad}`;
    }

    function textoValor(v) {
        if (v.valor == null) return '—';
        if (v.clave === 'puerta_abierta') return v.valor >= 1 ? 'Abierta' : 'Cerrada';
        if (v.clave === 'bandeja_unidades' || v.clave === 'clientes_acumulados' || v.clave === 'bandeja_peso_g') {
            return Math.round(v.valor).toString();
        }
        return Number(v.valor).toFixed(1);
    }

    function mostrarTarjetas(variables) {
        tarjetas.innerHTML = '';
        variables.forEach(v => {
            const col = document.createElement('div');
            col.className = 'col-12 col-sm-6 col-lg-3';
            const valor = textoValor(v);
            // la puerta abierta se resalta, aunque la alerta la envia el ESP32 recien a los 10 s
            if (v.clave === 'puerta_abierta' && v.valor >= 1) v.estado = 'alerta';
            const etiquetaEstado = v.estado === 'alerta' ? '<span class="badge bg-danger">Fuera de rango</span>'
                : v.estado === 'normal' ? '<span class="badge bg-success">Normal</span>'
                    : '<span class="badge bg-secondary">Sin datos</span>';
            col.innerHTML = `
                <div class="card shadow-sm h-100 tarjeta-sensor ${v.estado}">
                    <div class="card-body">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <i class="fas ${iconos[v.clave] || 'fa-gauge'} fa-lg text-secondary"></i>
                            ${etiquetaEstado}
                        </div>
                        <div class="small text-muted"></div>
                        <div class="valor"><span class="numero"></span> <small class="fs-6 text-muted"></small></div>
                        <div class="small text-muted mt-1">Rango normal: <span class="rango"></span></div>
                    </div>
                </div>`;
            // los textos se asignan con textContent (sin interpretar HTML)
            col.querySelector('.small.text-muted').textContent = v.nombre;
            col.querySelector('.numero').textContent = valor;
            col.querySelector('.valor small').textContent = v.unidad;
            col.querySelector('.rango').textContent = rango(v);
            if (v.minimo == null && v.maximo == null) {
                col.querySelector('.rango').parentElement.textContent = v.descripcion;
            }
            tarjetas.appendChild(col);
        });
    }

    function mostrarGraficos(variables) {
        if (typeof Chart === 'undefined') return;
        variables.filter(v => v.graficar).forEach(v => {
            if (!charts[v.clave]) {
                const col = document.createElement('div');
                col.className = 'col-12 col-lg-6';
                col.innerHTML = `<div class="card shadow-sm"><div class="card-body">
                    <h2 class="h6 titulo"></h2><div class="contenedor-grafico"><canvas></canvas></div></div></div>`;
                col.querySelector('.titulo').textContent = `${v.nombre} (${v.unidad})`;
                graficos.appendChild(col);
                charts[v.clave] = new Chart(col.querySelector('canvas'), {
                    type: 'line',
                    data: { labels: [], datasets: [] },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        animation: false,
                        plugins: { legend: { position: 'bottom', labels: { boxWidth: 12 } } },
                        scales: { x: { ticks: { maxTicksLimit: 6 } } }
                    }
                });
            }
            const chart = charts[v.clave];
            const lineaFija = (valor, etiqueta) => ({
                label: etiqueta, data: v.etiquetas.map(() => valor), borderColor: '#dc3545',
                borderDash: [6, 4], borderWidth: 1, pointRadius: 0
            });
            const datasets = [{
                label: v.nombre, data: v.valores, borderColor: '#8b6a4e', backgroundColor: 'rgba(139,106,78,0.15)',
                fill: true, tension: 0.25, pointRadius: 0, borderWidth: 2
            }];
            if (v.minimo != null) datasets.push(lineaFija(v.minimo, 'Mínimo'));
            if (v.maximo != null) datasets.push(lineaFija(v.maximo, 'Máximo'));
            chart.data.labels = v.etiquetas;
            chart.data.datasets = datasets;
            chart.update();
        });
    }

    function mostrarAlertas(alertas) {
        const pendientes = alertas.filter(a => !a.atendida).length;
        contadorAlertas.className = 'badge ' + (pendientes > 0 ? 'bg-danger' : 'bg-success');
        contadorAlertas.textContent = pendientes + ' pendientes';

        tablaAlertas.innerHTML = '';
        if (alertas.length === 0) {
            tablaAlertas.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Sin alertas</td></tr>';
            return;
        }
        alertas.forEach(a => {
            const fila = document.createElement('tr');
            if (!a.atendida) fila.className = 'table-danger';
            fila.innerHTML = '<td class="text-nowrap"></td><td><span class="badge"></span></td><td></td><td class="text-end"></td>';
            fila.children[0].textContent = a.fecha;
            const prioridad = fila.children[1].firstChild;
            prioridad.className = 'badge ' + (coloresPrioridad[a.prioridad] || 'bg-secondary');
            prioridad.textContent = a.prioridad || '—';
            fila.children[2].textContent = a.mensaje;
            if (a.atendida) {
                fila.children[3].innerHTML = '<span class="text-success small"><i class="fas fa-check me-1"></i>Atendida</span>';
            } else {
                const boton = document.createElement('button');
                boton.className = 'btn btn-sm btn-outline-success';
                boton.textContent = 'Marcar atendida';
                boton.addEventListener('click', () => atender(a.id));
                fila.children[3].appendChild(boton);
            }
            tablaAlertas.appendChild(fila);
        });
    }

    async function atender(id) {
        await fetch(`/api/iot/alertas/${id}/atender`, { method: 'POST' });
        actualizar();
    }
});
