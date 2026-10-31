package com.proyecto.panaderialosandes.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;

// pruebas unitarias del servicio de usuarios (sin base de datos)
class UsuarioServiceTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    private Usuarios crearUsuario(String rol, String password) {
        Usuarios usuario = new Usuarios();
        usuario.setNombre("Prueba");
        usuario.setUsername("prueba");
        usuario.setRol(rol);
        usuario.setPassword(password);
        return usuario;
    }

    @Test
    void guardarUsuarioEncriptaLaContrasenaYNormalizaElRol() {
        Usuarios usuario = crearUsuario("vendedor", "Clave123!");

        usuarioService.guardarUsuario(usuario);

        assertEquals("VENDEDOR", usuario.getRol());
        assertTrue(passwordEncoder.matches("Clave123!", usuario.getPassword()));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizarUsuarioNoVuelveAEncriptarLaContrasena() {
        String hashOriginal = passwordEncoder.encode("Clave123!");
        Usuarios usuario = crearUsuario("admin", hashOriginal);

        usuarioService.actualizarUsuario(usuario);

        assertEquals(hashOriginal, usuario.getPassword());
        assertTrue(passwordEncoder.matches("Clave123!", usuario.getPassword()));
    }

    @Test
    void rolInvalidoEsRechazado() {
        Usuarios usuario = crearUsuario("gerente", "Clave123!");

        assertThrows(IllegalArgumentException.class, () -> usuarioService.guardarUsuario(usuario));
        verify(usuarioRepository, never()).save(usuario);
    }
}
