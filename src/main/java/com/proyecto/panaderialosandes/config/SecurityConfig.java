package com.proyecto.panaderialosandes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // clave con la que se firma la cookie de "Recordarme" (en Render se puede definir RECORDARME_CLAVE)
    @Value("${app.seguridad.recordarme-clave:panaderia-los-andes-recordarme}")
    private String claveRecordarme;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/principal","/login", "/css/**", "/js/**", "/imagenes/**").permitAll()
                // catalogo de Realidad Aumentada para clientes (sin sesion): pagina, datos publicos, QR y modelos 3D
                .requestMatchers("/catalogo", "/catalogo/**", "/api/catalogo", "/modelos/**").permitAll()
                // entrada HTTP alternativa del ESP32 (sin sesion); el controlador valida la cabecera X-API-KEY
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/iot/telemetria", "/api/iot/alertas").permitAll()
                // automatizaciones programadas (GitHub Actions); el controlador valida la cabecera X-API-KEY
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/automatizaciones/*/ejecutar").permitAll()
                .requestMatchers("/principal/automatizaciones", "/principal/automatizaciones/**").hasRole("ADMIN")
                // el manual del administrador describe opciones que el vendedor no tiene
                .requestMatchers("/info/manual-administrador.pdf").hasRole("ADMIN")
                .requestMatchers( "/principal/venta", "/principal/productos","/productos/buscar").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers("/login/nuevo","/principal/listar_usuarios", "/principal/reporte**","/api/reporte-ventas/**","/productos/**").hasRole("ADMIN")
                // administracion de usuarios y reportes: solo ADMIN
                .requestMatchers("/login/guardar", "/login/actualizar/**", "/login/eliminar-usuario/**", "/login/dataUsuarios",
                        "/principal/cambiar_estado", "/api/reportes/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/principal/inicio", true)
                .permitAll()
            )
            // casilla "Recordarme" del login: mantiene la sesion 7 dias en ese navegador
            .rememberMe(recordar -> recordar
                .key(claveRecordarme)
                .rememberMeParameter("recordarme")
                .tokenValiditySeconds(7 * 24 * 60 * 60)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/principal/acceso_denegado")
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}