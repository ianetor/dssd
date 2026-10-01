package com.dssd.backend;

import com.dssd.backend.services.BonitaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BonitaPublicacionTest {
    BonitaService bonita;
    MockRestServiceServer server;

    @BeforeEach void preparar() {
        bonita = mock(BonitaService.class, CALLS_REAL_METHODS);
        RestTemplate template = new RestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        ReflectionTestUtils.setField(bonita, "restTemplate", template);
        ReflectionTestUtils.setField(bonita, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(bonita, "bonitaUrl", "http://bonita");
        doReturn(new HttpHeaders()).when(bonita).login();
    }

    @Test void seleccionaPublicacionAunqueOtraTareaSeaLaPrimera() {
        server.expect(requestTo("http://bonita/API/bpm/humanTask?p=0&c=100&f=caseId=10&f=state=ready"))
                .andRespond(withSuccess("[{\"id\":99,\"name\":\"Realizar Ofertas\"},{\"id\":20,\"name\":\"Revisar Emergencia y Generar Lotes\"}]", MediaType.APPLICATION_JSON));
        assertThat(bonita.buscarTareaPublicacion(10L)).isEqualTo(20L);
        server.verify();
    }

    @Test void noConfundeAusenciaDeTareaConPublicacionExitosa() {
        server.expect(anything()).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> bonita.buscarTareaPublicacion(10L)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test void reconciliaRespuestaPerdidaSinReejecutar() {
        server.expect(requestTo("http://bonita/API/bpm/humanTask/20")).andRespond(withResourceNotFound());
        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/API/bpm/archivedHumanTask");
            assertThat(request.getURI().getQuery()).contains("state=completed");
        }).andRespond(withSuccess("[{\"sourceObjectId\":20,\"parentCaseId\":10,\"name\":\"Revisar Emergencia y Generar Lotes\",\"state\":\"completed\"}]", MediaType.APPLICATION_JSON));
        bonita.confirmarPublicacion(10L, 20L);
        server.verify();
    }

    @Test void tareaDeOtroCasoNoConfirmaPublicacion() {
        server.expect(anything()).andRespond(withSuccess("{\"parentCaseId\":11,\"name\":\"Revisar Emergencia y Generar Lotes\",\"state\":\"ready\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> bonita.confirmarPublicacion(10L, 20L)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test void ejecutaSoloLaTareaGuardadaConContratoActualVacio() {
        server.expect(requestTo("http://bonita/API/bpm/humanTask/20"))
                .andRespond(withSuccess("{\"parentCaseId\":10,\"name\":\"Revisar Emergencia y Generar Lotes\",\"state\":\"ready\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://bonita/API/system/session/unusedid"))
                .andRespond(withSuccess("{\"user_id\":5}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://bonita/API/bpm/humanTask/20")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(requestTo("http://bonita/API/bpm/userTask/20/execution?assign=true&user=5"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{}"))
                .andRespond(withNoContent());
        bonita.confirmarPublicacion(10L, 20L);
        server.verify();
    }
}
