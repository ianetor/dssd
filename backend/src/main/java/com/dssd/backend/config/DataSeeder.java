package com.dssd.backend.config;

import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataSeeder {

    // @Bean
    // CommandLineRunner initDatabase(
    //         EmergenciaRepository emergenciaRepo,
    //         LoteNecesidadRepository loteRepo,
    //         OfertaAyudaRepository ofertaRepo,
    //         DetalleOfertaRepository detalleRepo) {
    //     return args -> {
    //         // Evitar duplicar los datos si ya existen
    //         if (emergenciaRepo.count() > 0) {
    //             System.out.println("✅ Base de datos ya inicializada previamente.");
    //             return;
    //         }

    //         System.out.println("🔄 Inicializando datos semilla en la base de datos...");

    //         // 1. Crear Emergencia (Incendio)
    //         Incendio incendio = new Incendio();
    //         incendio.setNivelGravedad("ALTO");
    //         incendio.setZonaAfectada("Bosque Norte");
    //         incendio.setDescripcion("Incendio forestal masivo");
    //         incendio.setEstado("REGISTRADA");
    //         incendio.setMunicipioNombre("user_rosario");
    //         incendio.setHectareasAfectadas(500.5); // Atributo específico de Incendio
    //         emergenciaRepo.save(incendio);

    //         // 2. Crear Lotes de Necesidad asociados a la emergencia
    //         LoteNecesidad loteAgua = new LoteNecesidad(null, "Agua Potable (Litros)", 10000, 0, incendio);
    //         LoteNecesidad loteParamedicos = new LoteNecesidad(null, "Paramédicos", 15, 0, incendio);
    //         loteRepo.saveAll(List.of(loteAgua, loteParamedicos));

    //         // 3. Crear Oferta de Ayuda de una ONG
    //         OfertaAyuda oferta = new OfertaAyuda();
    //         oferta.setEstado("BORRADOR");
    //         oferta.setNivelHabilitacion(5);
    //         oferta.setRecursosBloqueados(false);
    //         oferta.setOngLider("cruz_roja");
    //         ofertaRepo.save(oferta);

    //         // 4. Crear Detalles de Oferta asociados a la Oferta y a los Lotes
    //         DetalleOferta det1 = new DetalleOferta(null, 5000, oferta, loteAgua);
    //         DetalleOferta det2 = new DetalleOferta(null, 5, oferta, loteParamedicos);
    //         detalleRepo.saveAll(List.of(det1, det2));

    //         System.out.println("✅ Base de datos inicializada exitosamente con datos de prueba.");
    //     };
    // }
}
