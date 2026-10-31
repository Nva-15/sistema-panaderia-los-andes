package com.proyecto.panaderialosandes.services;

import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService implements UsuarioServiceInterface, UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    public List<Usuarios> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuarios> buscarPorId(int id) {
        return usuarioRepository.findById(id);
    }

    public void guardarUsuario(Usuarios usuario) {
        String rol = usuario.getRol().toUpperCase();
        if (!rol.equals("ADMIN") && !rol.equals("VENDEDOR")) {
            throw new IllegalArgumentException("El rol debe ser ADMIN o VENDEDOR");
        }
        usuario.setRol(rol);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    // actualiza los datos sin volver a encriptar la contraseña que ya esta guardada
    public void actualizarUsuario(Usuarios usuario) {
        String rol = usuario.getRol().toUpperCase();
        if (!rol.equals("ADMIN") && !rol.equals("VENDEDOR")) {
            throw new IllegalArgumentException("El rol debe ser ADMIN o VENDEDOR");
        }
        usuario.setRol(rol);
        usuarioRepository.save(usuario);
    }

    // restablece la contraseña (boton editar de la lista de usuarios). Mismos requisitos que al crear un usuario
    public void cambiarPassword(Usuarios usuario, String nuevaPassword) {
        if (!passwordValida(nuevaPassword)) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 5 caracteres, una mayúscula, una minúscula, un número y un carácter especial");
        }
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
    }

    static boolean passwordValida(String password) {
        return password != null && password.length() >= 5
                && password.matches(".*[A-Z].*") && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*") && password.matches(".*[\\W_].*");
    }

    public void cambiarEstadoUsuario(int id, String nuevoEstado) {
        usuarioRepository.findById(id).ifPresent(usuario -> {
            usuario.setEstado(nuevoEstado);
            usuarioRepository.save(usuario);
        });
    }

    public void eliminarUsuario(int id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    public Optional<Usuarios> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }   

    public Object listarVendedoresYAdmin() {
        return usuarioRepository.findByRolIn(List.of("ADMIN", "VENDEDOR"));
    }
}