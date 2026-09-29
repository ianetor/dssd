package com.dssd.backend.services;

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
    private HttpHeaders login() {
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

    // 2. Obtener el ID del proceso "Proceso1"
    public String getProcessDefinitionId(String processName, String processVersion, HttpHeaders headers) {
        String url = bonitaUrl + "/API/bpm/process?p=0&c=10&f=name=" + processName + "&f=version=" + processVersion;
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray() && root.size() > 0) {
                return root.get(0).get("id").asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 3. Crear instancia de la emergencia y setear variables
    public String iniciarInstanciaEmergencia(Long emergenciaId, String nivelGravedad, String zonaAfectada) {
        HttpHeaders headers = login();
        
        // Buscamos el ID interno de "Proceso1" con versión "1.0"
        String processId = getProcessDefinitionId("Proceso1", "1.0", headers);
        
        if (processId == null) {
            throw new RuntimeException("No se encontró el proceso 'Proceso1' desplegado en Bonita");
        }

        String caseUrl = bonitaUrl + "/API/bpm/case";

        // Estructura del body para crear el caso en Bonita
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("processDefinitionId", processId);

        // Variables de proceso iniciales
        List<Map<String, Object>> variables = new ArrayList<>();
        
        Map<String, Object> var1 = new HashMap<>();
        var1.put("name", "emergenciaId");
        var1.put("value", emergenciaId);
        variables.add(var1);

        Map<String, Object> var2 = new HashMap<>();
        var2.put("name", "nivelGravedad");
        var2.put("value", nivelGravedad);
        variables.add(var2);

        Map<String, Object> var3 = new HashMap<>();
        var3.put("name", "zonaAfectada");
        var3.put("value", zonaAfectada);
        variables.add(var3);

        requestBody.put("variables", variables);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(caseUrl, entity, String.class);

        return response.getBody(); // Retorna el JSON con el ID de la instancia creada
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