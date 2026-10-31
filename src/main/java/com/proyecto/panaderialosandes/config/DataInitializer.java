package com.proyecto.panaderialosandes.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.proyecto.panaderialosandes.models.Categorias;
import com.proyecto.panaderialosandes.models.Usuarios;
import com.proyecto.panaderialosandes.repositorios.CategoriaRepository;
import com.proyecto.panaderialosandes.repositorios.UsuarioRepository;
import com.proyecto.panaderialosandes.services.UsuarioService;

// carga los datos minimos para que el sistema funcione en una base de datos nueva (por ejemplo en la nube)
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public DataInitializer(CategoriaRepository categoriaRepository, UsuarioRepository usuarioRepository,
            UsuarioService usuarioService) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(String... args) {
        // las categorias se usan con id 1, 2 y 3 en los formularios
        if (categoriaRepository.count() == 0) {
            for (String nombre : List.of("Panes", "Tortas", "Pasteles")) {
                Categorias categoria = new Categorias();
                categoria.setNombre(nombre);
                categoriaRepository.save(categoria);
            }
            logger.info("Categorias iniciales creadas");
        }

        if (usuarioRepository.count() == 0) {
            if (adminPassword == null || adminPassword.isBlank()) {
                logger.warn("No hay usuarios y no se definio ADMIN_PASSWORD: no se creo el administrador inicial");
                return;
            }
            Usuarios admin = new Usuarios();
            admin.setNombre("Administrador");
            admin.setUsername(adminUsername);
            admin.setPassword(adminPassword);
            admin.setRol("ADMIN");
            usuarioService.guardarUsuario(admin);
            logger.info("Administrador inicial '{}' creado", adminUsername);
        }
    }
}
