(function () {
    function iniciarAsistenteFlotante() {
        const raiz = document.getElementById('asistenteFlotante');
        if (!raiz) return;

        const panel = raiz.querySelector('#panelAsistenteFlotante');
        const botonAbrir = raiz.querySelector('.boton-asistente-flotante');
        const botonCerrar = raiz.querySelector('.cerrar-asistente-flotante');
        const conversacion = raiz.querySelector('.conversacion-asistente-flotante');
        const formulario = raiz.querySelector('.form-asistente-flotante');
        const entrada = raiz.querySelector('.entrada-asistente-flotante');
        const botonEnviar = raiz.querySelector('.enviar-asistente-flotante');
        const historial = [];
        let ocupado = false;

        botonAbrir.addEventListener('click', function () {
            panel.hidden = !panel.hidden;
            botonAbrir.setAttribute('aria-expanded', String(!panel.hidden));
            if (!panel.hidden) entrada.focus();
        });

        botonCerrar.addEventListener('click', function () {
            panel.hidden = true;
            botonAbrir.setAttribute('aria-expanded', 'false');
            botonAbrir.focus();
        });

        formulario.addEventListener('submit', async function (evento) {
            evento.preventDefault();
            const pregunta = entrada.value.trim();
            if (!pregunta || ocupado) return;

            entrada.value = '';
            historial.push({ rol: 'usuario', texto: pregunta });
            agregarMensaje('usuario', pregunta);
            const mensajePendiente = agregarMensaje('respuesta', 'Consultando el sistema...');
            ocupado = true;
            botonEnviar.disabled = true;

            try {
                const respuesta = await fetch('/api/asistente/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ modo: 'CHAT', mensajes: historial })
                });
                const datos = await respuesta.json();
                mensajePendiente.remove();

                if (!respuesta.ok || !datos.exito) {
                    historial.pop();
                    agregarMensaje('error', datos.texto || 'No se pudo obtener respuesta.');
                    return;
                }

                historial.push({ rol: 'asistente', texto: datos.texto });
                agregarMensaje('respuesta', datos.texto);
            } catch (error) {
                console.error('Error del asistente:', error);
                historial.pop();
                mensajePendiente.remove();
                agregarMensaje('error', 'No se pudo conectar con el asistente.');
            } finally {
                ocupado = false;
                botonEnviar.disabled = false;
                entrada.focus();
            }
        });

        function agregarMensaje(tipo, texto) {
            const mensaje = document.createElement('p');
            mensaje.className = 'mensaje-asistente-flotante ' + tipo;
            mensaje.textContent = texto;
            conversacion.appendChild(mensaje);
            conversacion.scrollTop = conversacion.scrollHeight;
            return mensaje;
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciarAsistenteFlotante, { once: true });
    } else {
        iniciarAsistenteFlotante();
    }
})();
