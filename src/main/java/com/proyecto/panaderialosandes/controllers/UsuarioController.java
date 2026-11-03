package com.proyecto.panaderialosandes.controllers;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpSession;
import com.proyecto.panaderialosandes.dto.UsuarioDto;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.services.UsuarioService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Controller
@RequestMapping("/login")
public class UsuarioController {
    
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioService usuarioService, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String mostrarLogin() {
        return "vista/login";
    }

    @PostMapping
    public String procesarLogin(@RequestParam String username, 
                              @RequestParam String password, 
                              Model model, 
                              HttpSession session) {
        try {
            if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                model.addAttribute("error", "Credenciales no pueden estar vacías");
                return "vista/login";
            }
            
            UserDetails userDetails = usuarioService.loadUserByUsername(username);
            Usuarios usuario = (Usuarios) userDetails;
            
            if (!"activo".equalsIgnoreCase(usuario.getEstado())) {
                model.addAttribute("error", "Usuario inactivo");
                return "vista/login";
            }
            
            if (passwordEncoder.matches(password, usuario.getPassword())) {
                session.setAttribute("usuarioActual", usuario);
                return "redirect:/principal/inicio";
            } else {
                model.addAttribute("error", "Credenciales incorrectas");
                return "vista/login";
            }
        } catch (UsernameNotFoundException e) {
            model.addAttribute("error", "Usuario no encontrado");
            return "vista/login";
        }
    }

    @GetMapping("/nuevo")
    public String mostrarFormulario(Model model) {
        model.addAttribute("usuario", new Usuarios());
        return "vista/agregar_usuario";
    }

    @PostMapping("/guardar")
    public String guardarUsuario(@ModelAttribute Usuarios usuario, Model model) {
        try {
            if (usuario.getRol() == null || usuario.getRol().isEmpty()) {
                model.addAttribute("error", "El rol es requerido");
                return "vista/agregar_usuario";
            }
            
            String rol = usuario.getRol().toUpperCase();
            if (!rol.equals("ADMIN") && !rol.equals("VENDEDOR")) {
                model.addAttribute("error", "El rol debe ser ADMIN o VENDEDOR");
                return "vista/agregar_usuario";
            }
            
            usuario.setRol(rol);
            usuarioService.guardarUsuario(usuario);
            return "redirect:/principal/listar_usuarios";
        } catch (Exception e) {
            model.addAttribute("error", "Error al guardar usuario: " + e.getMessage());
            return "vista/agregar_usuario";
        }
    }

    @GetMapping("/principal/inicio")
    public String mostrarInicio(Model model, HttpSession session) {
        Usuarios usuario = (Usuarios) session.getAttribute("usuarioActual");
        if (usuario == null) {
            return "redirect:/login";
        }
        model.addAttribute("nombre", usuario.getNombre());
        model.addAttribute("rol", usuario.getRol());
        return "vista/principal";
    }

    @GetMapping("/dataUsuarios")
    @ResponseBody
    public ResponseEntity<List<UsuarioDto>> enviarData() {
        List<Usuarios> usuarios = usuarioService.obtenerTodosLosUsuarios();
        List<UsuarioDto> data = usuarios.stream()
            .map(u -> new UsuarioDto(
                u.getId(), 
                u.getNombre(), 
                u.getUsername(), 
                u.getRol(), 
                u.getEstado()))
            .toList();
        return ResponseEntity.ok(data);
    }

    @PostMapping("/actualizar/{id}")
    @ResponseBody
    public ResponseEntity<String> actualizarUsuario(
            @PathVariable int id, 
            @RequestBody ActualizacionUsuario usuarioDto) {
        try {
            Optional<Usuarios> usuarioOptional = usuarioService.buscarPorId(id);
            if (usuarioOptional.isPresent()) {
                Usuarios usuario = usuarioOptional.get();
                usuario.setNombre(usuarioDto.getNombre());
                usuario.setUsername(usuarioDto.getUsername());
                
                String rol = usuarioDto.getRol().toUpperCase();
                if (!rol.equals("ADMIN") && !rol.equals("VENDEDOR")) {
                    return ResponseEntity.badRequest().body("El rol debe ser ADMIN o VENDEDOR");
                }
                
                usuario.setRol(rol);
                // la contraseña solo cambia si el administrador escribio una nueva
                if (usuarioDto.getNuevaPassword() != null && !usuarioDto.getNuevaPassword().isBlank()) {
                    usuarioService.cambiarPassword(usuario, usuarioDto.getNuevaPassword());
                }
                usuarioService.actualizarUsuario(usuario);
                return ResponseEntity.ok("Usuario actualizado");
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/eliminar-usuario/{id}")
    public String eliminarUsuario(@PathVariable int id) {
        usuarioService.buscarPorId(id).ifPresent(u -> usuarioService.eliminarUsuario(id));
        return "redirect:/principal/listar_usuarios";
    }

    @GetMapping("/usuarioLogged")
    @ResponseBody
    public ResponseEntity<List<UsuarioDto>> enviarUsuario(@AuthenticationPrincipal Usuarios userAtenticado) {
        
        if(userAtenticado == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        Optional<Usuarios> usuarioOptional = usuarioService.buscarPorId(userAtenticado.getId());

        if (usuarioOptional.isPresent()) {
            Usuarios usuario = usuarioOptional.get();
            List<UsuarioDto> data = List.of(new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getUsername(),
                usuario.getRol(),
                usuario.getEstado()
            ));
            return ResponseEntity.ok(data);
        }

        return ResponseEntity.notFound().build();
    }
    
        

    // datos que envia el modal de edicion (actualizar_usuario.js)
    @lombok.Data
    public static class ActualizacionUsuario {
        private String nombre;
        private String username;
        private String rol;
        private String nuevaPassword;
    }
}
