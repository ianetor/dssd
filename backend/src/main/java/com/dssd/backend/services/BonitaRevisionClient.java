package com.dssd.backend.services;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.*;

/** Cuenta técnica independiente de la sesión humana; las decisiones guardan al coordinador real. */
@Service
public class BonitaRevisionClient {
    private final String url, username, password;
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private record Sesion(HttpHeaders headers, String userId) {}
    public BonitaRevisionClient(@Value("${bonita.url:${BONITA_URL:http://localhost:8081/bonita}}") String url,
            @Value("${BONITA_REVIEW_USERNAME:rescuesync.integration}") String username,
            @Value("${BONITA_REVIEW_PASSWORD:}") String password, ObjectMapper mapper) {
        this.url = url; this.username = username; this.password = password; this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
        this.http = new RestTemplate(factory);
    }
    private Sesion login() {
        if (password.isBlank()) throw new IllegalStateException("Configure BONITA_REVIEW_PASSWORD y la cuenta técnica en Studio");
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("username", username); form.add("password", password); form.add("redirect", "false");
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var response = http.postForEntity(url + "/loginservice", new HttpEntity<>(form, headers), String.class);
        List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream().map(c -> c.split(";",2)[0]).toList();
        String token = cookies.stream().filter(c -> c.startsWith("X-Bonita-API-Token=")).map(c -> c.substring("X-Bonita-API-Token=".length())).findFirst().orElseThrow();
        headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, String.join("; ", cookies)); headers.set("X-Bonita-API-Token", token);
        return new Sesion(headers, get("/API/system/session/unusedid", headers).path("user_id").asText());
    }
    private JsonNode get(String path, HttpHeaders headers) {
        try { return mapper.readTree(http.exchange(url + path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Respuesta inválida de Bonita", ex); }
    }
    private void procesoCompatible(JsonNode tarea, HttpHeaders headers) {
        var proceso = get("/API/bpm/process/" + tarea.path("processId").asText(), headers);
        if (!"RescueSync".equals(proceso.path("name").asText()) || !"1.0".equals(proceso.path("version").asText())) {
            throw new IllegalStateException("La tarea requiere RescueSync 1.0; no se modifica un proceso anterior");
        }
    }
    public Long buscarTarea(Long caseId) {
        var sesion = login();
        for (int p = 0; ; p++) {
            var tareas = get("/API/bpm/humanTask?p=" + p + "&c=100&f=caseId=" + caseId + "&f=state=ready", sesion.headers());
            for (var tarea : tareas) {
                if (tarea.path("parentCaseId").asLong() == caseId && "Evaluar cobertura insuficiente".equals(tarea.path("name").asText())) {
                    procesoCompatible(tarea, sesion.headers()); return tarea.path("id").asLong();
                }
            }
            if (tareas.size() < 100) return null;
        }
    }
    private boolean archivada(Long caseId, Long taskId, HttpHeaders headers) {
        for (int p = 0; ; p++) {
            var tareas = get("/API/bpm/archivedHumanTask?p=" + p + "&c=100&f=state=completed&f=name=Evaluar cobertura insuficiente", headers);
            for (var tarea : tareas) {
                if (tarea.path("parentCaseId").asLong() == caseId && tarea.path("sourceObjectId").asLong() == taskId
                        && "completed".equals(tarea.path("state").asText())
                        && "Evaluar cobertura insuficiente".equals(tarea.path("name").asText())) return true;
            }
            if (tareas.size() < 100) return false;
        }
    }
    public void completar(Long caseId, Long taskId, String decisionId, String decision, String motivo, String coordinador) {
        var sesion = login();
        JsonNode tarea;
        try { tarea = get("/API/bpm/humanTask/" + taskId, sesion.headers()); }
        catch (HttpClientErrorException.NotFound ex) {
            if (archivada(caseId, taskId, sesion.headers())) {
                var idEjecutado = get("/API/bpm/caseVariable/" + caseId + "/decisionId", sesion.headers()).path("value").asText();
                var accionEjecutada = get("/API/bpm/caseVariable/" + caseId + "/decisionCoordinador", sesion.headers()).path("value").asText();
                if (!decisionId.equals(idEjecutado) || !decision.equals(accionEjecutada)) {
                    throw new IllegalStateException("La tarea archivada no corresponde a la decisión registrada");
                }
                return;
            }
            throw new IllegalStateException("No se pudo confirmar la tarea de decisión", ex);
        }
        if (tarea.path("parentCaseId").asLong() != caseId || !"Evaluar cobertura insuficiente".equals(tarea.path("name").asText())
                || !"ready".equals(tarea.path("state").asText())) throw new IllegalStateException("La tarea de decisión no está disponible para este caso");
        procesoCompatible(tarea, sesion.headers());
        var inputs = get("/API/bpm/userTask/" + taskId + "/contract", sesion.headers()).path("inputs");
        Set<String> names = new HashSet<>(); boolean text = inputs.isArray();
        for (var input : inputs) { names.add(input.path("name").asText()); text &= "TEXT".equals(input.path("type").asText()) && !input.path("multiple").asBoolean(); }
        if (!text || inputs.size() != 4 || !names.equals(Set.of("decisionInput", "motivoInput", "decisionIdInput", "coordinadorInput"))) {
            throw new IllegalStateException("Contrato de decisión incompatible; configure las cuatro entradas TEXT en Studio");
        }
        http.put(url + "/API/bpm/humanTask/" + taskId, new HttpEntity<>(Map.of("assigned_id", sesion.userId()), sesion.headers()));
        http.postForEntity(url + "/API/bpm/userTask/" + taskId + "/execution", new HttpEntity<>(Map.of(
                "decisionInput", decision, "motivoInput", motivo, "decisionIdInput", decisionId,
                "coordinadorInput", coordinador), sesion.headers()), String.class);
    }
    public boolean avanceConfirmado(Long caseId, boolean cancelar, int nuevaRonda) {
        var sesion = login();
        if (cancelar) {
            for (int p = 0; ; p++) {
                var casos = get("/API/bpm/archivedCase?p=" + p + "&c=100", sesion.headers());
                for (var caso : casos) if (caso.path("sourceObjectId").asLong() == caseId
                        && "completed".equals(caso.path("state").asText())) return true;
                if (casos.size() < 100) return false;
            }
        }
        var ronda = get("/API/bpm/caseVariable/" + caseId + "/rondaConvocatoria", sesion.headers());
        if (ronda.path("value").asInt() < nuevaRonda) return false;
        var tareas = get("/API/bpm/humanTask?p=0&c=100&f=caseId=" + caseId + "&f=state=ready", sesion.headers());
        for (var tarea : tareas) if (tarea.path("parentCaseId").asLong() == caseId
                && "Realizar Ofertas".equals(tarea.path("name").asText())) return true;
        return false;
    }
}
