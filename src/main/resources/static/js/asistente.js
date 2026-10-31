document.addEventListener('DOMContentLoaded', function () {
    const chat = document.getElementById('chat');
    const formChat = document.getElementById('formChat');
    const inputPregunta = document.getElementById('pregunta');
    const btnEnviar = document.getElementById('btnEnviar');
    const btnLimpiar = document.getElementById('btnLimpiar');

    // historial de la conversacion que se envia al servidor en cada pregunta
    let historial = [];
    let ocupado = false;

    const nombresHerramientas = {
        consultar_ventas: 'ventas',
        consultar_inventario: 'inventario'
    };

    formChat.addEventListener('submit', function (e) {
        e.preventDefault();
        const texto = inputPregunta.value.trim();
        if (!texto) return;
        inputPregunta.value = '';
        enviar('CHAT', texto);
    });

    document.querySelectorAll('.pregunta-sugerida').forEach(btn => {
        btn.addEventListener('click', () => enviar('CHAT', btn.dataset.texto));
    });

    // las acciones rapidas inician una conversacion nueva con su propio modo (prompt few-shot / one-shot)
    document.querySelectorAll('.accion-rapida').forEach(btn => {
        btn.addEventListener('click', () => {
            historial = [];
            enviar(btn.dataset.modo, btn.textContent.trim(), true);
        });
    });

    btnLimpiar.addEventListener('click', function () {
        historial = [];
        chat.querySelectorAll('.mensaje:not(:first-child)').forEach(m => m.remove());
    });

    async function enviar(modo, texto, esAccion = false) {
        if (ocupado) return;
        ocupado = true;
        btnEnviar.disabled = true;

        agregarMensaje('usuario', texto);
        // en las acciones rapidas el servidor arma la solicitud segun el modo
        if (!esAccion) {
            historial.push({ rol: 'usuario', texto: texto });
        }
        const cargando = agregarMensaje('asistente', '<i class="fas fa-spinner fa-spin me-2"></i><span class="estado">Consultando el sistema...</span>', true);
        // contador de segundos para que el usuario vea que la consulta sigue en proceso
        const estado = cargando.querySelector('.estado');
        const inicio = Date.now();
        const temporizador = setInterval(() => {
            const segundos = Math.round((Date.now() - inicio) / 1000);
            estado.textContent = (segundos < 8 ? 'Consultando el sistema... ' : 'Analizando los datos... ') + segundos + ' s';
        }, 1000);

        try {
            const response = await fetch('/api/asistente/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ modo: modo, mensajes: historial })
            });
            const data = await response.json();
            cargando.remove();

            if (data.exito) {
                const burbuja = agregarMensaje('asistente', markdownSeguro(data.texto), true);
                if (data.herramientasUsadas && data.herramientasUsadas.length > 0) {
                    const consultas = [...new Set(data.herramientasUsadas)]
                        .map(h => nombresHerramientas[h] || h).join(', ');
                    const nota = document.createElement('div');
                    nota.className = 'nota-herramientas';
                    nota.textContent = 'Datos consultados: ' + consultas;
                    burbuja.querySelector('.burbuja').appendChild(nota);
                }
                if (esAccion) {
                    historial.push({ rol: 'usuario', texto: texto });
                }
                historial.push({ rol: 'asistente', texto: data.texto });
            } else {
                agregarMensaje('error', data.texto || 'No se pudo obtener respuesta.');
                if (!esAccion) historial.pop();
            }
        } catch (error) {
            console.error('Error del asistente:', error);
            cargando.remove();
            agregarMensaje('error', 'No se pudo conectar con el servidor.');
            if (!esAccion) historial.pop();
        } finally {
            clearInterval(temporizador);
            ocupado = false;
            btnEnviar.disabled = false;
            inputPregunta.focus();
        }
    }

    function agregarMensaje(tipo, contenido, esHtml = false) {
        const mensaje = document.createElement('div');
        mensaje.className = 'mensaje ' + tipo;
        const burbuja = document.createElement('div');
        burbuja.className = 'burbuja';
        if (esHtml) {
            burbuja.innerHTML = contenido;
        } else {
            burbuja.textContent = contenido;
        }
        mensaje.appendChild(burbuja);
        chat.appendChild(mensaje);
        chat.scrollTop = chat.scrollHeight;
        return mensaje;
    }

    // convierte el Markdown de la respuesta en HTML. Primero escapa todo el texto, por lo que
    // ningun contenido (por ejemplo el nombre de un producto) puede inyectar HTML o scripts.
    function markdownSeguro(texto) {
        const escapar = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
        const enLinea = s => escapar(s)
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/`([^`]+)`/g, '<code>$1</code>');

        const lineas = texto.split('\n');
        let html = '';
        let i = 0;
        while (i < lineas.length) {
            const linea = lineas[i];

            // tabla: lineas consecutivas que empiezan con |
            if (linea.trim().startsWith('|')) {
                const filas = [];
                while (i < lineas.length && lineas[i].trim().startsWith('|')) {
                    filas.push(lineas[i].trim());
                    i++;
                }
                html += tabla(filas, enLinea);
                continue;
            }
            // lista con viñetas
            if (/^\s*[-*] /.test(linea)) {
                html += '<ul>';
                while (i < lineas.length && /^\s*[-*] /.test(lineas[i])) {
                    html += '<li>' + enLinea(lineas[i].replace(/^\s*[-*] /, '')) + '</li>';
                    i++;
                }
                html += '</ul>';
                continue;
            }
            // lista numerada
            if (/^\s*\d+\. /.test(linea)) {
                html += '<ol>';
                while (i < lineas.length && /^\s*\d+\. /.test(lineas[i])) {
                    html += '<li>' + enLinea(lineas[i].replace(/^\s*\d+\. /, '')) + '</li>';
                    i++;
                }
                html += '</ol>';
                continue;
            }
            // titulos
            const titulo = linea.match(/^(#{1,4}) (.*)$/);
            if (titulo) {
                html += '<p class="fw-bold mb-1">' + enLinea(titulo[2]) + '</p>';
            } else if (linea.trim() === '') {
                html += '<div class="espacio"></div>';
            } else {
                html += '<p class="mb-1">' + enLinea(linea) + '</p>';
            }
            i++;
        }
        return html;
    }

    function tabla(filas, enLinea) {
        const celdas = fila => fila.replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim());
        const esSeparador = fila => /^\|?\s*:?-{2,}/.test(fila);
        let html = '<div class="table-responsive"><table class="table table-sm table-bordered mb-2">';
        filas.forEach((fila, indice) => {
            if (esSeparador(fila)) return;
            const etiqueta = indice === 0 ? 'th' : 'td';
            html += '<tr>' + celdas(fila).map(c => `<${etiqueta}>${enLinea(c)}</${etiqueta}>`).join('') + '</tr>';
        });
        return html + '</table></div>';
    }
});
