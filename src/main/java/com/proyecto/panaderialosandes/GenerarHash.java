package com.proyecto.panaderialosandes;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerarHash {
    public static void main(String[] args) {
        java.io.Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Ejecute este programa desde una consola interactiva");
        }

        char[] password = console.readPassword("Ingrese la contraseña: ");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashReal;
        try {
            hashReal = encoder.encode(new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        System.out.println("=========================================");
        System.out.println("TU HASH REAL ES: " + hashReal);
        System.out.println("=========================================");
    }
}
