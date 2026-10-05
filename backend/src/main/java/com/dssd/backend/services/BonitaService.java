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

    private String bonitaUrl;

    private String username;

    private String password;

    @Value("${BONITA_PROCESS_NAME:RescueSync}")
    private String processName;

    @Value("${BONITA_PROCESS_VERSION:1.0}")
    private String processVersion;

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
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        this.restTemplate.setRequestFactory(requestFactory);
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
        String sessionCookie = null;

        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.contains("JSESSIONID=")) {
                    sessionCookie = cookie.split(";")[0];
                }
                if (cookie.contains("X-Bonita-API-Token=")) {
                    apiToken = cookie.split(";")[0].replace("X-Bonita-API-Token=", "");
                }
            }
        }

        HttpHeaders reqHeaders = new HttpHeaders();
        if (sessionCookie == null || apiToken.isEmpty()) {
            throw new IllegalStateException("Bonita no devolvió las credenciales de sesión");
        }
        jsessionId = sessionCookie;
        reqHeaders.add(HttpHeaders.COOKIE, sessionCookie + "; X-Bonita-API-Token=" + apiToken);
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

        String processId = getProcessDefinitionId(processName, processVersion, headers);
        if (processId == null) {
            throw new RuntimeException("No se encontró el proceso configurado en Bonita");
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
            throw new RuntimeException("Error al instanciar el proceso '" + processName + "' versión " + processVersion + " en Bonita", e);
        }
    }

    public String listProcesses() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("p", "0");
        params.put("c", "100");
        return get("/API/bpm/process", params);
    }

    public String listPendingTasks() {
        HttpHeaders headers = login();
        String url = bonitaUrl + "/API/bpm/humanTask?p=0&c=100&f=state=ready";
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        return response.getBody();
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

    private static final String TAREA_PUBLICACION = "Revisar Emergencia y Generar Lotes";

    public Long buscarTareaPublicacion(Long caseId) {
        HttpHeaders headers = login();
        JsonNode tareas = leerPublicacion("/API/bpm/humanTask?p=0&c=100&f=caseId=" + caseId + "&f=state=ready", headers);
        Long encontrada = null;
        for (JsonNode tarea : tareas) {
            if (TAREA_PUBLICACION.equals(tarea.path("name").asText())) {
                if (encontrada != null) throw new IllegalStateException("Más de una tarea de publicación para el caso");
                encontrada = tarea.path("id").asLong();
            }
        }
        if (encontrada == null) throw new IllegalStateException("La tarea de publicación no está disponible");
        return encontrada;
    }

    public void confirmarPublicacion(Long caseId, Long taskId, Long emergenciaId,
                                     java.time.Instant fechaVencimiento) {
        HttpHeaders headers = login();
        JsonNode tarea;
        try {
            tarea = leerPublicacion("/API/bpm/humanTask/" + taskId, headers);
        } catch (HttpClientErrorException.NotFound ex) {
            for (int pagina = 0; ; pagina++) {
                // sourceObjectId no es un filtro documentado de archivedHumanTask.
                JsonNode archivadas = leerPublicacion("/API/bpm/archivedHumanTask?p=" + pagina
                        + "&c=100&f=state=completed&f=name=" + TAREA_PUBLICACION, headers);
                for (JsonNode archivada : archivadas) {
                    if (archivada.path("sourceObjectId").asLong() == taskId
                            && archivada.path("parentCaseId").asLong() == caseId
                            && TAREA_PUBLICACION.equals(archivada.path("name").asText())
                            && "completed".equals(archivada.path("state").asText())) return;
                }
                if (archivadas.size() < 100) break;
            }
            throw new IllegalStateException("No se pudo verificar la ejecución de la tarea de publicación", ex);
        }
        if (tarea.path("parentCaseId").asLong() != caseId || !TAREA_PUBLICACION.equals(tarea.path("name").asText())) {
            throw new IllegalStateException("La tarea no corresponde a la publicación del caso");
        }
        if ("completed".equals(tarea.path("state").asText())) return;
        if (!"ready".equals(tarea.path("state").asText())) throw new IllegalStateException("Tarea de publicación no ejecutable");
        // Consultar la definición y el contrato de ESTA tarea, no los de casos nuevos.
        String processId = tarea.path("processId").asText();
        if (processId.isBlank()) throw new IllegalStateException("Bonita no indicó la definición de la tarea");
        JsonNode proceso = leerPublicacion("/API/bpm/process/" + processId, headers);
        if (!processName.equals(proceso.path("name").asText())) {
            throw new IllegalStateException("La tarea pertenece a otro proceso");
        }
        JsonNode inputs = leerPublicacion("/API/bpm/userTask/" + taskId + "/contract", headers).path("inputs");
        if (!inputs.isArray()) {
            throw new IllegalStateException("Bonita no indicó los inputs del contrato de publicación");
        }
        Set<String> nombresInputs = new HashSet<>();
        boolean inputsTextSimples = true;
        for (JsonNode input : inputs) {
            nombresInputs.add(input.path("name").asText());
            inputsTextSimples &= "TEXT".equals(input.path("type").asText())
                    && input.has("multiple") && !input.path("multiple").asBoolean();
        }
        Map<String, Object> contrato;
        if (inputs.size() == 2 && inputsTextSimples
                && nombresInputs.equals(Set.of("emergenciaIdInput", "fechaVencimientoEpochMillisInput"))) {
            if (emergenciaId == null || emergenciaId <= 0 || fechaVencimiento == null) {
                throw new IllegalStateException("La publicación requiere emergencia y vencimiento persistidos");
            }
            // Long solo está disponible en contratos del pool. En tareas usamos TEXT
            // y las operaciones de Bonita convierten cada cadena a una variable Long.
            contrato = Map.of("emergenciaIdInput", emergenciaId.toString(),
                    "fechaVencimientoEpochMillisInput", Long.toString(fechaVencimiento.toEpochMilli()));
        } else {
            throw new IllegalStateException("Contrato de publicación incompatible: se requieren emergenciaIdInput y fechaVencimientoEpochMillisInput de tipo TEXT, no múltiples");
        }
        String userId = getUserId(headers);
        if (userId == null) throw new IllegalStateException("No se pudo identificar el usuario de Bonita");
        restTemplate.put(bonitaUrl + "/API/bpm/humanTask/" + taskId,
                new HttpEntity<>(Map.of("assigned_id", userId), headers));
        restTemplate.postForEntity(bonitaUrl + "/API/bpm/userTask/" + taskId + "/execution?assign=true&user=" + userId,
                new HttpEntity<>(contrato, headers), String.class);
    }

    private static final String TAREA_OFERTAS = "Realizar Ofertas";
    private static final String TAREA_ADJUDICAR = "Adjudicar Ofertas";
    private static final String TAREA_INSUFICIENTE = "Evaluar cobertura insuficiente";

    private java.util.List<JsonNode> tareasActivasCobertura(Long caseId, HttpHeaders headers) {
        if (caseId == null) throw new IllegalStateException("La emergencia no tiene caso de Bonita asociado");
        java.util.List<JsonNode> tareas = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            JsonNode lote = leerPublicacion("/API/bpm/humanTask?p=" + pagina
                    + "&c=100&f=caseId=" + caseId + "&f=state=ready", headers);
            if (!lote.isArray()) throw new IllegalStateException("Lista de tareas de Bonita inválida");
            for (JsonNode tarea : lote) {
                if (tarea.path("parentCaseId").asLong() != caseId) {
                    throw new IllegalStateException("Bonita devolvió una tarea de otro caso");
                }
                tareas.add(tarea);
            }
            if (lote.size() < 100) return tareas;
        }
    }

    public boolean adjudicacionDisponible(Long caseId) {
        HttpHeaders headers = login();
        var tareas = tareasActivasCobertura(caseId, headers);
        if (tareas.stream().anyMatch(t -> TAREA_INSUFICIENTE.equals(t.path("name").asText()))) {
            throw new IllegalStateException("El caso llegó a Evaluar cobertura insuficiente pese a tener cobertura completa. Revisá la consulta de cobertura y la condición de Bonita.");
        }
        if (tareas.stream().anyMatch(t -> TAREA_ADJUDICAR.equals(t.path("name").asText()))) return true;
        // El usuario pudo adjudicar antes del reintento tras una respuesta perdida.
        // Los filtros documentados del historial son name/state; validar el caso localmente.
        for (int pagina = 0; ; pagina++) {
            JsonNode archivadas = leerPublicacion("/API/bpm/archivedHumanTask?p=" + pagina
                    + "&c=100&f=state=completed&f=name=" + TAREA_ADJUDICAR, headers);
            if (!archivadas.isArray()) throw new IllegalStateException("Historial de tareas de Bonita inválido");
            for (JsonNode tarea : archivadas) {
                if (tarea.path("parentCaseId").asLong() == caseId
                        && TAREA_ADJUDICAR.equals(tarea.path("name").asText())
                        && "completed".equals(tarea.path("state").asText())) return true;
            }
            if (archivadas.size() < 100) return false;
        }
    }

    public Long buscarTareaOfertas(Long caseId) {
        var tareas = tareasActivasCobertura(caseId, login()).stream()
                .filter(t -> TAREA_OFERTAS.equals(t.path("name").asText())).toList();
        if (tareas.size() != 1) throw new IllegalStateException("Esperando la tarea Realizar Ofertas o la adjudicación en Bonita");
        return tareas.get(0).path("id").asLong();
    }

    public void completarRecepcionPorCobertura(Long caseId, Long taskId) {
        HttpHeaders headers = login();
        JsonNode tarea = leerPublicacion("/API/bpm/humanTask/" + taskId, headers);
        if (tarea.path("parentCaseId").asLong() != caseId
                || !TAREA_OFERTAS.equals(tarea.path("name").asText())
                || !"ready".equals(tarea.path("state").asText())) {
            throw new IllegalStateException("Solo se puede completar Realizar Ofertas del caso cerrado por cobertura");
        }
        JsonNode proceso = leerPublicacion("/API/bpm/process/" + tarea.path("processId").asText(), headers);
        if (!processName.equals(proceso.path("name").asText())) throw new IllegalStateException("La tarea pertenece a otro proceso");
        JsonNode contrato = leerPublicacion("/API/bpm/userTask/" + taskId + "/contract", headers);
        if (!contrato.path("inputs").isArray() || !contrato.path("inputs").isEmpty()) {
            throw new IllegalStateException("Realizar Ofertas debe conservar un contrato sin entradas para el avance por cobertura");
        }
        String userId = getUserId(headers);
        if (userId == null) throw new IllegalStateException("No se pudo identificar el usuario de Bonita");
        restTemplate.put(bonitaUrl + "/API/bpm/humanTask/" + taskId,
                new HttpEntity<>(Map.of("assigned_id", userId), headers));
        restTemplate.postForEntity(bonitaUrl + "/API/bpm/userTask/" + taskId + "/execution?assign=true&user=" + userId,
                new HttpEntity<>(Map.of(), headers), String.class);
    }

    private JsonNode leerPublicacion(String path, HttpHeaders headers) {
        String body = restTemplate.exchange(bonitaUrl + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Respuesta de Bonita inválida", ex);
        }
    }

    private String getUserId(HttpHeaders headers) {
        // Intentar obtener el userId de la sesión actual
        try {
            String sessionUrl = bonitaUrl + "/API/system/session/unusedid";
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(sessionUrl, HttpMethod.GET, entity, String.class);
            JsonNode tree = objectMapper.readTree(resp.getBody());
            if (tree.has("user_id")) {
                String uid = tree.get("user_id").asText();
                System.out.println("UserId obtenido de sesión Bonita: " + uid);
                return uid;
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo sesión de Bonita: " + e.getMessage());
        }

        // Fallback: buscar por userName
        try {
            String url = bonitaUrl + "/API/identity/user?p=0&c=1&f=userName%3d" + username;
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            System.out.println("Respuesta identity/user: " + resp.getBody());
            JsonNode tree = objectMapper.readTree(resp.getBody());
            if (tree.isArray() && tree.size() > 0) {
                return tree.get(0).get("id").asText();
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo ID del usuario " + username + ": " + e.getMessage());
        }
        return null;
    }

}
