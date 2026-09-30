
package com.dssd.backend;
import com.dssd.backend.models.*;
import com.dssd.backend.repositories.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
@DataJpaTest
@Rollback(value = false)
public class DomainPersistenceTest {   
    
    @Autowired private EmergenciaRepository emergenciaRepo;
    @Autowired private LoteNecesidadRepository loteRepo;
    @Autowired private OfertaAyudaRepository ofertaRepo;
    @Autowired private DetalleOfertaRepository detalleRepo;
    @Test
    @DisplayName("Debe persistir y recuperar correctamente los diferentes tipos de Emergencia por herencia")
    public void testPersistirYRecuperarHerenciaEmergencias() {
        // Arrange - Incendio
        Incendio incendio = new Incendio();
        incendio.setDescripcion("Incendio forestal en reserva");
        incendio.setNivelGravedad("ALTO");
        incendio.setZonaAfectada("Zona Norte");
        incendio.setEstado("REGISTRADA");
        incendio.setMunicipioNombre("municipio_laplata");
        incendio.setHectareasAfectadas(250.5);
        // Arrange - Inundacion
        Inundacion inundacion = new Inundacion();
        inundacion.setDescripcion("Crecida del río");
        inundacion.setNivelGravedad("MEDIA");
        inundacion.setZonaAfectada("Ribera Este");
        inundacion.setEstado("CONVOCATORIA_ABIERTA");
          inundacion.setMunicipioNombre("municipio_rosario");
        inundacion.setMilimetrosAgua(120.0);
        // Act
        emergenciaRepo.save(incendio);
        emergenciaRepo.save(inundacion);
        // Assert - Buscar todas
        List<Emergencia> emergencias = emergenciaRepo.findAll();
        assertThat(emergencias).hasSize(2);
        // Assert - Buscar por estado
        List<Emergencia> registradas = emergenciaRepo.findByEstado("REGISTRADA");
        assertThat(registradas).hasSize(1);
        assertThat(registradas.get(0).getMunicipioNombre()).isEqualTo("municipio_laplata");
        assertThat(registradas.get(0)).isInstanceOf(Incendio.class);
        assertThat(((Incendio) registradas.get(0)).getHectareasAfectadas()).isEqualTo(250.5);
    }
@Test
    @DisplayName("Debe persistir Lotes de Necesidad asociados a una Emergencia")
    public void testPersistirEmergenciaConLotes() {
        // Arrange
        Terremoto terremoto = new Terremoto();
        terremoto.setDescripcion("Sismo de mediana intensidad");
        terremoto.setNivelGravedad("CRITICO");
        terremoto.setZonaAfectada("Sector Centro");
        terremoto.setEstado("REGISTRADA");
        terremoto.setMunicipioNombre("municipio_mendoza");
        terremoto.setMagnitudRichter(6.4);
        Emergencia emergenciaGuardada = emergenciaRepo.save(terremoto);
        LoteNecesidad loteAgua = new LoteNecesidad(null, "Agua Potable", 5000, 0, emergenciaGuardada);
        LoteNecesidad loteCarpas = new LoteNecesidad(null, "Carpas de campaña", 100, 0, emergenciaGuardada);
        loteRepo.saveAll(List.of(loteAgua, loteCarpas));
        // Act
        List<LoteNecesidad> lotesRecuperados = loteRepo.findByEmergenciaId(emergenciaGuardada.getId());
        // Assert
        assertThat(lotesRecuperados).hasSize(2);
        assertThat(lotesRecuperados).extracting(LoteNecesidad::getTipoRecurso)
                .containsExactlyInAnyOrder("Agua Potable", "Carpas de campaña");
        assertThat(lotesRecuperados.get(0).getEmergencia().getMunicipioNombre()).isEqualTo("municipio_mendoza");
    }

        @Test
    @DisplayName("Debe persistir OfertaAyuda con consorcio de ONGs representadas por nombre")
    public void testPersistirOfertaAyudaConsorcio() {
        // Arrange
        OfertaAyuda oferta = new OfertaAyuda();
        oferta.setEstado("PUBLICADA");
        oferta.setNivelHabilitacion(4);
        oferta.setRecursosBloqueados(false);
        oferta.setOngLider("cruz_roja");
        oferta.getOngColaboradoras().add("caritas");
        oferta.getOngColaboradoras().add("bomberos_voluntarios");
        // Act
        OfertaAyuda guardada = ofertaRepo.save(oferta);
        // Assert
        OfertaAyuda recuperada = ofertaRepo.findById(guardada.getId()).orElse(null);
        assertThat(recuperada).isNotNull();
        assertThat(recuperada.getOngLider()).isEqualTo("cruz_roja");
        assertThat(recuperada.getOngColaboradoras()).containsExactly("caritas", "bomberos_voluntarios");
    }
@Test
    @DisplayName("Debe persistir DetalleOferta vinculando OfertaAyuda y LoteNecesidad")
    public void testPersistirDetalleOferta() {
        // Arrange
        Incendio incendio = new Incendio();
        incendio.setDescripcion("Fuego pastizales");
        incendio.setMunicipioNombre("municipio_cordoba");
        incendio.setEstado("CONVOCATORIA_ABIERTA");
        emergenciaRepo.save(incendio);
        LoteNecesidad lote = new LoteNecesidad(null, "Herramientas de mano", 50, 0, incendio);
        loteRepo.save(lote);
        OfertaAyuda oferta = new OfertaAyuda();
        oferta.setEstado("EN_EVALUACION");
        oferta.setOngLider("ong_cordoba_rescatistas");
        ofertaRepo.save(oferta);
        DetalleOferta detalle = new DetalleOferta(null, 30, oferta, lote);
        detalleRepo.save(detalle);
        // Act
        DetalleOferta recuperado = detalleRepo.findById(detalle.getId()).orElse(null);
        // Assert
        assertThat(recuperado).isNotNull();
        assertThat(recuperado.getCantidadOfrecida()).isEqualTo(30);
        assertThat(recuperado.getOferta().getOngLider()).isEqualTo("ong_cordoba_rescatistas");
        assertThat(recuperado.getLote().getEmergencia().getMunicipioNombre()).isEqualTo("municipio_cordoba");
    }
}
