package com.proyecto.panaderialosandes.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.proyecto.panaderialosandes.models.Categorias;
import com.proyecto.panaderialosandes.models.Productos;

class ExcelExportServiceTest {

    @Test
    void generaUnExcelConEncabezadoYUnaFilaPorProducto() throws Exception {
        Productos producto = new Productos();
        producto.setId(1);
        producto.setNombre("Pan frances");
        producto.setCategoria_id(new Categorias(1, "Panes"));
        producto.setCantidad(100);
        producto.setPrecio(0.30);
        producto.setUnidad("unidad");

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        new ExcelExportService().generarReporteProductos(List.of(producto), salida);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
            Sheet hoja = workbook.getSheet("Productos");
            assertEquals(2, hoja.getPhysicalNumberOfRows());
            assertEquals("Nombre", hoja.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Pan frances", hoja.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Panes", hoja.getRow(1).getCell(2).getStringCellValue());
        }
    }
}
