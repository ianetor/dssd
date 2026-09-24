package com.dssd.backend.services;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class BonitaClientService {

    private final RestClient restClient;
    private final String bonitaUrl;
    private final String username;
    private final String password;

    private volatile String jsessionId;

    public BonitaClientService(
            @Value("${BONITA_URL}") String bonitaUrl,
            @Value("${BONITA_USERNAME}") String username,
            @Value("${BONITA_PASSWORD}") String password) {
        this.restClient = RestClient.builder().baseUrl(bonitaUrl).build();
        this.bonitaUrl = bonitaUrl;
        this.username = username;
        this.password = password;
    }

    public synchronized void login() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", this.username);
        form.add("password", this.password);

        ResponseEntity<Void> response = restClient.post()
                .uri("/loginservice?redirect=false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        this.jsessionId = extractJSessionId(setCookie);
        if (this.jsessionId == null) {
            throw new IllegalStateException("No se recibio JSESSIONID del login de Bonita");
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

    public String listCases() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        return get("/API/bpm/case", params);
    }

    public String listUsers() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        return get("/API/identity/user", params);
    }

    public String getResource(String resource, Map<String, String> queryParams) {
        return get("/API/" + resource, queryParams);
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

    public String startProcess(String processId, String variablesJson) {
        return post("/API/bpm/process/" + processId + "/instantiation", variablesJson);
    }

    public String completeHumanTask(String taskId, String bodyJson) {
        return post("/API/bpm/userTask/" + taskId + "/execution", bodyJson);
    }

    public String get(String path, Map<String, String> queryParams) {
        return execute(() -> restClient.get()
                .uri(buildUri(path, queryParams))
                .cookie("JSESSIONID", requireSession())
                .retrieve()
                .body(String.class));
    }

    public String post(String path, String body) {
        return execute(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie("JSESSIONID", requireSession())
                .body(body)
                .retrieve()
                .body(String.class));
    }

    private String buildUri(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.toUriString();
    }

    private String requireSession() {
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

    private String extractJSessionId(String setCookie) {
        if (setCookie == null) {
            return null;
        }
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("JSESSIONID=")) {
                return trimmed.substring("JSESSIONID=".length());
            }
        }
        return null;
    }
}