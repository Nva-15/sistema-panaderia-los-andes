// gemelo digital: la escena 3D refleja en vivo los datos del ESP32 (los mismos del dashboard IoT)
document.addEventListener('DOMContentLoaded', function () {
    const estadoEnVivo = document.getElementById('estadoEnVivo');
    const vidrioVitrina = document.getElementById('vidrioVitrina');
    const textoVitrina = document.getElementById('textoVitrina');
    const textoBandeja = document.getElementById('textoBandeja');
    const textoPuerta = document.getElementById('textoPuerta');
    const textoClientes = document.getElementById('textoClientes');
    const bisagra = document.getElementById('bisagra');
    const panes = document.getElementById('panes');
    const ficha = document.getElementById('ficha');
    const MAX_PANES = 12;
    let datos = {};

    // fichas de capacitacion para el personal nuevo
    const fichas = {
        vitrina: {
            titulo: 'Vitrina de tortas y pasteles',
            dato: () => `Ahora: ${valor('vitrina_temp_c', 1)} °C · ${valor('vitrina_hum_pct', 0)} % de humedad`,
            pasos: ['La temperatura debe mantenerse en 25 °C o menos y la humedad en 70 % o menos.',
                'Si la vitrina se ve roja, hay una alerta: revisa que no le dé el sol ni el calor del horno.',
                'Las tortas con crema no deben pasar más de 4 horas fuera de refrigeración.']
        },
        bandeja: {
            titulo: 'Bandeja de pan (celda de carga)',
            dato: () => `Ahora: ${valor('bandeja_peso_g', 0)} g ≈ ${valor('bandeja_unidades', 0)} panes`,
            pasos: ['El sensor pesa la bandeja y calcula los panes a 60 g cada uno.',
                'Con menos de 10 panes llega un aviso al Telegram del equipo: reponer desde el horno.',
                'Coloca los panes sin apilar para que el peso sea correcto.']
        },
        horno: {
            titulo: 'Horno de producción',
            dato: () => 'Pan francés: 180–200 °C por 15–18 minutos',
            pasos: ['Precalienta el horno 20 minutos antes de la primera tanda.',
                'Revisa a las 7 p. m. el plan de producción que envía el agente de IA por Telegram.',
                'Usa guantes térmicos y nunca dejes el horno encendido sin supervisión.']
        },
        puerta: {
            titulo: 'Puerta del almacén',
            dato: () => `Ahora: ${datos.puerta_abierta >= 1 ? 'ABIERTA' : 'cerrada'}`,
            pasos: ['Mantén la puerta cerrada: protege la harina de la humedad y de plagas.',
                'Si queda abierta más de 10 segundos, el ESP32 suena y envía una alerta.',
                'Revisa que la puerta quede cerrada al terminar el turno.']
        },
        clientes: {
            titulo: 'Contador de clientes (sensor PIR)',
            dato: () => `Clientes detectados: ${valor('clientes_acumulados', 0)}`,
            pasos: ['El sensor de movimiento de la entrada cuenta las personas que ingresan.',
                'Estos datos ayudan a planificar el personal en las horas de más afluencia.']
        }
    };

    function valor(clave, decimales) {
        const v = datos[clave];
        return v == null ? '--' : Number(v).toFixed(decimales);
    }

    // puntos de informacion: funcionan con el mouse y con el control de los lentes VR
    document.querySelectorAll('.punto-info').forEach(punto => {
        punto.addEventListener('click', () => mostrarFicha(punto.dataset.ficha));
    });
    document.getElementById('cerrarFicha').addEventListener('click', () => ficha.classList.add('oculta'));

    let fichaAbierta = null;
    function mostrarFicha(clave) {
        const f = fichas[clave];
        if (!f) return;
        fichaAbierta = clave;
        document.getElementById('fichaTitulo').textContent = f.titulo;
        document.getElementById('fichaDato').textContent = f.dato();
        const lista = document.getElementById('fichaPasos');
        lista.innerHTML = '';
        f.pasos.forEach(p => {
            const li = document.createElement('li');
            li.textContent = p;
            lista.appendChild(li);
        });
        ficha.classList.remove('oculta');
    }

    actualizar();
    setInterval(actualizar, 5000);

    async function actualizar() {
        try {
            const response = await fetch('/api/iot/estado?horas=1');
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const estado = await response.json();
            datos = {};
            estado.variables.forEach(v => { datos[v.clave] = v.valor; datos[v.clave + '_estado'] = v.estado; });
            aplicar(estado);
        } catch (error) {
            estadoEnVivo.textContent = 'Sin conexión con el sistema';
            estadoEnVivo.className = 'estado-vivo desconectado';
        }
    }

    function aplicar(estado) {
        estadoEnVivo.textContent = estado.enLinea ? '● En vivo: ESP32 conectado' : '○ ESP32 sin conexión (últimos datos)';
        estadoEnVivo.className = 'estado-vivo ' + (estado.enLinea ? 'conectado' : 'desconectado');

        // vitrina: verde si todo esta normal, roja si temperatura o humedad estan fuera de rango
        const alertaVitrina = datos.vitrina_temp_c_estado === 'alerta' || datos.vitrina_hum_pct_estado === 'alerta';
        vidrioVitrina.setAttribute('material', 'color', alertaVitrina ? '#ff6b6b' : '#bfe6c8');
        textoVitrina.setAttribute('value', `Vitrina: ${valor('vitrina_temp_c', 1)} C | ${valor('vitrina_hum_pct', 0)} %`);
        textoVitrina.setAttribute('color', alertaVitrina ? '#b00020' : '#3b2512');

        // bandeja: se muestran tantos panes como indica el peso (hasta 12 modelos)
        const unidades = datos.bandeja_unidades == null ? 0 : Math.max(0, Math.round(datos.bandeja_unidades));
        const visibles = Math.min(MAX_PANES, Math.ceil(unidades / 5));
        dibujarPanes(visibles);
        textoBandeja.setAttribute('value', `Bandeja: ${unidades} panes`);
        textoBandeja.setAttribute('color', datos.bandeja_unidades_estado === 'alerta' || datos.bandeja_peso_g_estado === 'alerta' ? '#b00020' : '#3b2512');

        // puerta: se abre en la escena cuando el sensor indica que esta abierta
        const abierta = datos.puerta_abierta >= 1;
        bisagra.setAttribute('animation', `property: rotation; to: 0 ${abierta ? 80 : 0} 0; dur: 800; easing: easeInOutQuad`);
        textoPuerta.setAttribute('value', abierta ? 'Puerta: ABIERTA' : 'Puerta: cerrada');
        textoPuerta.setAttribute('color', abierta ? '#b00020' : '#3b2512');

        textoClientes.setAttribute('value', `Clientes hoy: ${valor('clientes_acumulados', 0)}`);

        // si hay una ficha abierta, su dato se actualiza en vivo
        if (fichaAbierta && !ficha.classList.contains('oculta')) {
            document.getElementById('fichaDato').textContent = fichas[fichaAbierta].dato();
        }
    }

    function dibujarPanes(cantidad) {
        if (panes.children.length === cantidad) return;
        while (panes.firstChild) panes.removeChild(panes.firstChild);
        for (let i = 0; i < cantidad; i++) {
            const pan = document.createElement('a-entity');
            pan.setAttribute('gltf-model', '#mPan');
            const fila = Math.floor(i / 4), columna = i % 4;
            pan.setAttribute('position', `${-0.3 + columna * 0.2} 0.02 ${-0.13 + fila * 0.13}`);
            panes.appendChild(pan);
        }
    }
});
