package com.dssd.backend;

import com.dssd.backend.dtos.EmergenciaRequestDTO;
import com.dssd.backend.dtos.LoteRequestDTO;
import com.dssd.backend.models.Municipio;
import com.dssd.backend.repositories.EmergenciaRepository;
import com.dssd.backend.repositories.MunicipioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class EmergenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MunicipioRepository municipioRepo;

    @Autowired
    private EmergenciaRepository emergenciaRepo;

    private Municipio municipioTest;

    @BeforeEach
    public void setup() {
        if (municipioRepo.count() == 0) {
            municipioTest = municipioRepo.save(new Municipio(null, "muni_test", "Municipio de Prueba"));
        } else {
            municipioTest = municipioRepo.findAll().get(0);
        }
    }

    @Test
    public void testRegistrarEmergenciaIncendio() throws Exception {
        EmergenciaRequestDTO requestDTO = new EmergenciaRequestDTO(
                "INCENDIO",
                "CRITICO",
                "Sector Serrano",
                "Fuego descontrolado en pinares",
                municipioTest.getId(),
                350.5,
                null,
                null
        );

        mockMvc.perform(post("/api/emergencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.tipoEmergencia").value("INCENDIO"))
                .andExpect(jsonPath("$.hectareasAfectadas").value(350.5))
                .andExpect(jsonPath("$.municipioId").value(municipioTest.getId()))
                .andExpect(jsonPath("$.lotes").isEmpty());
    }

    @Test
    public void testFlujoDesacoplado_RegistrarEmergenciaYPublicarLotesPosteriormente() throws Exception {
        
        EmergenciaRequestDTO requestDTO = new EmergenciaRequestDTO(
                "INUNDACION",
                "ALTO",
                "Ribera Sur",
                "Crecida del río con desborde en barrios costeros",
                municipioTest.getId(),
                null,
                180.0,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/emergencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.tipoEmergencia").value("INUNDACION"))
                .andExpect(jsonPath("$.milimetrosAgua").value(180.0))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        Long emergenciaId = objectMapper.readTree(responseContent).get("id").asLong();

        
        List<LoteRequestDTO> lotes = List.of(
                new LoteRequestDTO("Botes de rescate", 4),
                new LoteRequestDTO("Raciones de alimento", 500)
        );

        mockMvc.perform(post("/api/emergencias/" + emergenciaId + "/lotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(emergenciaId))
                .andExpect(jsonPath("$.estado").value("CONVOCATORIA_ABIERTA"))
                .andExpect(jsonPath("$.lotes", hasSize(2)))
                .andExpect(jsonPath("$.lotes[0].tipoRecurso").value("Botes de rescate"))
                .andExpect(jsonPath("$.lotes[0].cantidadRequerida").value(4))
                .andExpect(jsonPath("$.lotes[0].cantidadCubierta").value(0))
                .andExpect(jsonPath("$.lotes[1].tipoRecurso").value("Raciones de alimento"))
                .andExpect(jsonPath("$.lotes[1].cantidadRequerida").value(500))
                .andExpect(jsonPath("$.lotes[1].cantidadCubierta").value(0));

        
        mockMvc.perform(get("/api/emergencias/" + emergenciaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONVOCATORIA_ABIERTA"))
                .andExpect(jsonPath("$.lotes", hasSize(2)));
    }

    @Test
    public void testListarMunicipiosParaElFormulario() throws Exception {
        mockMvc.perform(get("/api/municipios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].nombre").isNotEmpty());
    }
}
