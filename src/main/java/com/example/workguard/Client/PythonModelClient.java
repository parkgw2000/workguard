package com.example.workguard.Client;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class PythonModelClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String pythonServerUrl = "http://localhost:8000/summarize";  // FastAPI 주소

    public Map<String, Object> sendLinesToPythonModel(List<String> lines) {
        Map<String, Object> requestBody = Map.of("text", String.join(" ", lines));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(pythonServerUrl, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("Python server 호출 실패: " + response.getStatusCode());
        }
    }
}