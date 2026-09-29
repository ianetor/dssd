package com.dssd.backend.services;

import com.dssd.backend.dtos.EmergenciasDTO.EmergenciaRequestDTO;
import com.dssd.backend.models.Emergencia;
import com.dssd.backend.models.LoteNecesidad;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;

@Service
public class BonitaService {

    @Value("${bonita.url:${BONITA_URL:http://localhost:8081/bonita}}")
    private String bonitaUrl;

    @Value("${bonita.username:${BONITA_USERNAME:install}}")
    private String username;

    @Value("${bonita.password:${BONITA_PASSWORD:install}}")
    private String password;

    private final RestClient restClient;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String jsessionId;

    public BonitaService(
            @Value("${bonita.url:${BONITA_URL:http://localhost:8081/bonita}}") String bonitaUrl,
            @Value("${bonita.username:${BONITA_USERNAME:install}}") String username,
            @Value("${bonita.password:${BONITA_PASSWORD:install}}") String password) {
        this.restClient = RestClient.builder().baseUrl(bonitaUrl).build();
        this.bonitaUrl = bonitaUrl;
        this.username = username;
        this.password = password;
    }

    // 1. Iniciar sesión y obtener cookies + token
    public HttpHeaders login() {
        String loginUrl = bonitaUrl + "/loginservice";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("password", password);
        map.add("redirect", "false");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, request, String.class);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String apiToken = "";

        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.contains("JSESSIONID=")) {
                    jsessionId = cookie.split(";")[0];
                }
                if (cookie.contains("X-Bonita-API-Token=")) {
                    apiToken = cookie.split(";")[0].replace("X-Bonita-API-Token=", "");
                }
            }
        }

        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.add(HttpHeaders.COOKIE, jsessionId + "; X-Bonita-API-Token=" + apiToken);
        reqHeaders.add("X-Bonita-API-Token", apiToken);
        reqHeaders.setContentType(MediaType.APPLICATION_JSON);

        return reqHeaders;
    }

    public String getProcessDefinitionId(String processName, String processVersion, HttpHeaders headers) {
        String url = UriComponentsBuilder.fromHttpUrl(bonitaUrl + "/API/bpm/process")
            .queryParam("p", 0)
            .queryParam("c", 10)
            .queryParam("f", "name=" + processName)
            .queryParam("f", "version=" + processVersion)
            .queryParam("f", "activationState=ENABLED")
            .build()
            .toUriString();

        HttpEntity requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            
            // 1. Obtener el body como String explícito
            String jsonBody = (String) response.getBody();

            if (jsonBody != null) {
                // 2. Pasar el String directamente a readTree
                JsonNode root = objectMapper.readTree(jsonBody);
                
                if (root.isArray() && !root.isEmpty()) {
                    return root.get(0).get("id").asText();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al consultar el processDefinitionId en Bonita para el proceso: " + processName, e);
    }

        throw new IllegalStateException("No se encontró ningún proceso habilitado con nombre '" + processName + "' y versión '" + processVersion + "' en Bonita.");
    }

    public Long iniciarInstanciaEmergencia(EmergenciaRequestDTO dto, Long emergenciaId) {
        HttpHeaders headers = login();
        System.out.println(headers);
        String processId = getProcessDefinitionId("RescueSync", "1.0", headers);
        System.out.println(processId);
        if (processId == null) {
            throw new RuntimeException("No se encontró el proceso 'Proceso1' desplegado en Bonita");
        }

        String caseUrl = bonitaUrl + "/API/bpm/process/" + processId + "/instantiation";

        Map<String, Object> emergenciaInput = new HashMap<>();
        emergenciaInput.put("tipoEmergencia", dto.getTipoEmergencia()); // Ojo: usa el nombre corregido
        emergenciaInput.put("nivelGravedad", dto.getNivelGravedad());
        emergenciaInput.put("zonaAfectada", dto.getZonaAfectada());
        emergenciaInput.put("descripcion", dto.getDescripcion());
        emergenciaInput.put("municipioNombre", dto.getMunicipioNombre());
        emergenciaInput.put("lotes", new ArrayList<LoteNecesidad>());

        Map<String,Object> requestBody = new HashMap<>();
        requestBody.put("emergenciaActualInput", emergenciaInput);
        HttpEntity requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            // 5. Enviar la petición POST a Bonita
            ResponseEntity<String> response = restTemplate.exchange(caseUrl, HttpMethod.POST, requestEntity, String.class);

            // 6. Parsear la respuesta para extraer el caseId
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("caseId")) {
                return root.get("caseId").asLong();
            }

            throw new RuntimeException("La respuesta de Bonita no incluyó el 'caseId'");

        } catch (Exception e) {
            throw new RuntimeException("Error al instanciar el proceso 'RescueSync' en Bonita: " + e.getMessage(), e);
        }
    }

    public String listProcesses() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        return get("/API/bpm/process", params);
    }

    public String listPendingTasks() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        params.put("f", "state=ready,state=claimed,state=started");
        return get("/API/bpm/task", params);
    }

    public String listUsers() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        return get("/API/identity/user", params);
    }

    public Map<String, Object> healthStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bonitaUrl", this.bonitaUrl);
        result.put("bonitaUsername", this.username);
        result.put("reachable", false);
        result.put("loggedIn", false);
        try {
            login();
            result.put("reachable", true);
            result.put("loggedIn", true);
        } catch (HttpClientErrorException ex) {
            result.put("reachable", true);
            result.put("error", ex.getMessage());
        } catch (Exception ex) {
            result.put("reachable", false);
            result.put("error", ex.getMessage());
        }
        return result;
    }

    private String get(String path, Map<String, String> queryParams) {
        return execute(() -> restClient.get()
                .uri(buildUri(path, queryParams))
                .cookie("JSESSIONID", requireSession())
                .retrieve()
                .body(String.class));
    }

    private String post(String path, String body) {
        return execute(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", requireSession())
                .body(body)
                .retrieve()
                .body(String.class));
    }

    public String buildUri(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.toUriString();
    }

    public String requireSession() {
        if (this.jsessionId == null) {
            login();
        }
        return this.jsessionId;
    }

    private String execute(java.util.function.Supplier<String> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.Unauthorized ex) {
            login();
            return request.get();
        }
    }

}
