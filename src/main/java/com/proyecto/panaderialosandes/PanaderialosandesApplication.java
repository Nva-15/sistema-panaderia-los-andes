package com.proyecto.panaderialosandes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PanaderialosandesApplication {

	public static void main(String[] args) {
		SpringApplication.run(PanaderialosandesApplication.class, args);
		System.out.println("Endpoint de reportes disponible en: /api/reporte-ventas/filtros");
	}

}
