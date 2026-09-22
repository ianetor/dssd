package com.dssd.backend.config;

import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(
            MunicipioRepository municipioRepo,
            OngRepository ongRepo,
            EmergenciaRepository emergenciaRepo,
            LoteNecesidadRepository loteRepo,
            OfertaAyudaRepository ofertaRepo,
            DetalleOfertaRepository detalleRepo) {
        return args -> {
            // Evitar duplicar los datos si ya existen
            if (municipioRepo.count() > 0) {
                System.out.println("✅ Base de datos ya inicializada previamente.");
                return;
            }

            System.out.println("🔄 Inicializando datos semilla en la base de datos...");

            // 1. Crear Municipio
            Municipio muni = new Municipio(null, "user_rosario", "Municipalidad de Rosario");
            municipioRepo.save(muni);

            // 2. Crear ONGs
            Ong ong1 = new Ong(null, "cruz_roja", "Cruz Roja Argentina", 5);
            Ong ong2 = new Ong(null, "caritas", "Cáritas", 4);
            ongRepo.saveAll(List.of(ong1, ong2));

            // 3. Crear Emergencia (Incendio)
            Incendio incendio = new Incendio();
            incendio.setNivelGravedad("ALTO");
            incendio.setZonaAfectada("Bosque Norte");
            incendio.setDescripcion("Incendio forestal masivo");
            incendio.setEstado("ACTIVA");
            incendio.setMunicipioAfectado(muni);
            incendio.setHectareasAfectadas(500.5); // Atributo específico de Incendio
            emergenciaRepo.save(incendio);

            // 4. Crear Lotes de Necesidad asociados a la emergencia
            LoteNecesidad loteAgua = new LoteNecesidad(null, "Agua Potable (Litros)", 10000, 0, incendio);
            LoteNecesidad loteParamedicos = new LoteNecesidad(null, "Paramédicos", 15, 0, incendio);
            loteRepo.saveAll(List.of(loteAgua, loteParamedicos));

            // 5. Crear Oferta de Ayuda (Consorcio)
            OfertaAyuda oferta = new OfertaAyuda();
            oferta.setEstado("BORRADOR");
            oferta.setNivelHabilitacion(5);
            oferta.setRecursosBloqueados(false);
            oferta.setOngLider(ong1);
            oferta.getOngsColaboradoras().add(ong2); // Asociar ONG colaboradora
            ofertaRepo.save(oferta);

            // 6. Crear Detalles de Oferta asociados a la Oferta y a los Lotes
            DetalleOferta det1 = new DetalleOferta(null, 5000, oferta, loteAgua);
            DetalleOferta det2 = new DetalleOferta(null, 5, oferta, loteParamedicos);
            detalleRepo.saveAll(List.of(det1, det2));

            System.out.println("✅ Base de datos inicializada exitosamente con datos de prueba.");
        };
    }
}
