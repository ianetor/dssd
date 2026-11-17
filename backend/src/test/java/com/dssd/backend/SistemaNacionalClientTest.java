package com.dssd.backend;

import com.dssd.backend.services.SistemaNacionalClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(value = SistemaNacionalClient.class, properties = {
        "NACIONAL_URL=http://nacional.test/", "NACIONAL_USERNAME=bonita", "NACIONAL_PASSWORD=clave-de-prueba"})
// El cliente cachea el token: cada test necesita su propia instancia.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SistemaNacionalClientTest {
    private static final String LOGIN = "http://nacional.test/api/auth/login";
    private static final String CATALOGO = "http://nacional.test/api/recursos/disponibilidad";
    private static final String RECURSOS = """
            [{"tipoRecurso":"Paramedicos","cantidadTotal":28,"cantidadBloqueada":5,
              "cantidadDisponible":23,"ongsAportantes":2}]""";

    @Autowired SistemaNacionalClient cliente;
    @Autowired MockRestServiceServer nacional;

    private static String token(String valor) {
        return "{\"accessToken\":\"" + valor + "\",\"tokenType\":\"Bearer\",\"expiresInSeconds\":3600}";
    }

    @Test
    @DisplayName("Hace login una sola vez y reutiliza el token en las consultas siguientes")
    void reutilizaElToken() {
        nacional.expect(ExpectedCount.once(), requestTo(LOGIN)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("bonita"))
                .andRespond(withSuccess(token("t1"), MediaType.APPLICATION_JSON));
        nacional.expect(ExpectedCount.twice(), requestTo(CATALOGO))
                .andExpect(header("Authorization", "Bearer t1"))
                .andRespond(withSuccess(RECURSOS, MediaType.APPLICATION_JSON));

        assertEquals(23, cliente.disponibilidad().get(0).cantidadDisponible());
        assertEquals("Paramedicos", cliente.disponibilidad().get(0).tipoRecurso());
        nacional.verify();
    }

    @Test
    @DisplayName("Si el token deja de valer, vuelve a autenticarse y reintenta una vez")
    void renuevaAnte401() {
        nacional.expect(requestTo(LOGIN)).andRespond(withSuccess(token("viejo"), MediaType.APPLICATION_JSON));
        nacional.expect(requestTo(CATALOGO)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        nacional.expect(requestTo(LOGIN)).andRespond(withSuccess(token("nuevo"), MediaType.APPLICATION_JSON));
        nacional.expect(requestTo(CATALOGO)).andExpect(header("Authorization", "Bearer nuevo"))
                .andRespond(withSuccess(RECURSOS, MediaType.APPLICATION_JSON));

        assertEquals(1, cliente.disponibilidad().size());
        nacional.verify();
    }

    @Test
    @DisplayName("Si el Sistema Nacional no responde, informa 503 en lugar de un error interno")
    void informaIndisponibilidad() {
        nacional.expect(requestTo(LOGIN)).andRespond(withServerError());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, cliente::disponibilidad);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }
}
