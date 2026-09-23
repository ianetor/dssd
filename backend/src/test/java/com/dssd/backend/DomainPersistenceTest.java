package com.dssd.backend;

import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class DomainPersistenceTest {

    @Autowired private EmergenciaRepository emergenciaRepo;
    @Autowired private OngRepository ongRepo;
    @Autowired private OfertaAyudaRepository ofertaRepo;
    @Autowired private MunicipioRepository municipioRepo;

    @Test
    public void testPersistirYRecuperarIncendio() {
        // Arrange
        Municipio muni = new Municipio(null, "muni_test", "Test City");
        municipioRepo.save(muni);

        Incendio incendio = new Incendio();
        incendio.setDescripcion("Fuego de prueba");
        incendio.setHectareasAfectadas(100.0);
        incendio.setMunicipioAfectado(muni);
        
        // Act
        emergenciaRepo.save(incendio);

        // Assert
        List<Emergencia> emergencias = emergenciaRepo.findAll();
        assertThat(emergencias).hasSize(1);
        
        // Verificar Single Table Inheritance
        assertThat(emergencias.get(0)).isInstanceOf(Incendio.class);
        assertThat(((Incendio) emergencias.get(0)).getHectareasAfectadas()).isEqualTo(100.0);
    }

    @Test
    public void testRelacionConsorcioOferta() {
        // Arrange
        Ong lider = new Ong(null, "ong1", "Lider", 1);
        Ong colab = new Ong(null, "ong2", "Colab", 1);
        ongRepo.save(lider);
        ongRepo.save(colab);

        OfertaAyuda oferta = new OfertaAyuda();
        oferta.setOngLider(lider);
        oferta.getOngsColaboradoras().add(colab);
        
        // Act
        ofertaRepo.save(oferta);

        // Assert
        OfertaAyuda recuperada = ofertaRepo.findById(oferta.getId()).orElse(null);
        assertThat(recuperada).isNotNull();
        assertThat(recuperada.getOngLider().getNombre()).isEqualTo("Lider");
        assertThat(recuperada.getOngsColaboradoras()).hasSize(1);
        assertThat(recuperada.getOngsColaboradoras().get(0).getNombre()).isEqualTo("Colab");
    }
}
