package com.proyecto.panaderialosandes.config;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/principal","/login", "/css/**", "/js/**", "/imagenes/**").permitAll()
                // entrada HTTP alternativa del ESP32 (sin sesion); el controlador valida la cabecera X-API-KEY
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/iot/telemetria", "/api/iot/alertas").permitAll()
                // automatizaciones programadas (GitHub Actions); el controlador valida la cabecera X-API-KEY
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/automatizaciones/*/ejecutar").permitAll()
                .requestMatchers("/principal/automatizaciones", "/principal/automatizaciones/**").hasRole("ADMIN")
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