document.addEventListener('DOMContentLoaded', function () {
    let dataUsuarios = [];
    let idUsuario = "";

    async function cargarDatosVentas() {
        try {
            const response = await fetch("/login/dataUsuarios");
            dataUsuarios = await response.json(); // Simplificado
        } catch (error) {
            console.error("Error al cargar datos:", error);
        }
    }

    //datos del formulario
    const nombreUsuario = document.getElementById("nombreEditar");
    const usernameUsuario = document.getElementById("usernameEditar");
    const rolUsuario = document.getElementById("rolEditar");
    const botonesEditar = document.querySelectorAll('[id^="btnEditar-"]')
    const btnGuardar = document.getElementById("btnGuardar");

    // este bloque solo aplica a la lista de usuarios (modal de edicion)
    if (!btnGuardar || !nombreUsuario) return;

    btnGuardar.addEventListener('click', guardarCambios);

    // Función para manejar el clic
    function manejarClickEditar(event) {
        const boton = event.currentTarget;
        idUsuario = boton.getAttribute('data-id');
        console.log("ID usuario:", idUsuario);

        // Aquí puedes cargar los datos del usuario en el modal
        const usuario = dataUsuarios.find(u => u.id == idUsuario);
        console.log("Usuario encontrado:", usuario);
        if (usuario) {
            nombreUsuario.value = usuario.nombre || '';
            usernameUsuario.value = usuario.username || '';
            rolUsuario.value = usuario.rol || '';
        }
    }

    // Cargar datos y configurar eventos
    cargarDatosVentas().then(() => {
        console.log("Datos cargados:", dataUsuarios);

        // Configurar event listeners para TODOS los botones de editar
        botonesEditar.forEach(boton => {
            boton.addEventListener('click', manejarClickEditar);
        });
    });

    function guardarCambios() {
        const nombre = nombreUsuario.value.trim();
        const username = usernameUsuario.value.trim();
        const rol = rolUsuario.value.trim();

        if (!idUsuario || !nombre || !username || !rol) return;

        const id = idUsuario;

        const datos = {
            id: idUsuario,
            nombre: nombreUsuario.value,
            username: usernameUsuario.value,
            rol: rolUsuario.value
        };

        fetch(`/login/actualizar/${id}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(datos)
        })
            .then(response => {
                if (response.ok) {
                    alert('Cambios guardados correctamente');
                    location.reload(); // Recargar la página
                }
            });
    }

});

document.addEventListener("DOMContentLoaded", function () {
    const passwordInput = document.getElementById("password");
    const icon = document.getElementById("iconPassword");
    const toggleBtn = document.getElementById("togglePassword");

    // este bloque solo aplica al formulario de nuevo usuario
    if (!passwordInput || !toggleBtn) return;

    const requirements = {
        length: document.getElementById("length"),
        uppercase: document.getElementById("uppercase"),
        lowercase: document.getElementById("lowercase"),
        number: document.getElementById("number"),
        special: document.getElementById("special")
    };

    const regex = {
        length: /.{5,}/,
        uppercase: /[A-Z]/,
        lowercase: /[a-z]/,
        number: /\d/,
        special: /[\W_]/ // incluye símbolos como !@#$ etc
    };

    passwordInput.addEventListener("input", () => {
        const value = passwordInput.value;

        for (let key in regex) {
            if (regex[key].test(value)) {
                requirements[key].classList.remove("text-danger");
                requirements[key].classList.add("text-success");
                requirements[key].textContent = `✅ ${requirements[key].textContent.slice(2)}`;
            } else {
                requirements[key].classList.remove("text-success");
                requirements[key].classList.add("text-danger");
                requirements[key].textContent = `❌ ${requirements[key].textContent.slice(2)}`;
            }
        }
    });

    // Mostrar / ocultar contraseña
    toggleBtn.addEventListener("click", function () {
        const type = passwordInput.getAttribute("type") === "password" ? "text" : "password";
        passwordInput.setAttribute("type", type);
        icon.classList.toggle("fa-eye");
        icon.classList.toggle("fa-eye-slash");
    });

    // Validar al enviar
    const form = passwordInput.form;
    form.addEventListener("submit", function (event) {
        const value = passwordInput.value;
        for (let key in regex) {
            if (!regex[key].test(value)) {
                event.preventDefault();
                alert("La contraseña no cumple con todos los requisitos.");
                passwordInput.focus();
                return;
            }
        }
    });
});
