package com.example.workguard.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ChatGptService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final WebClient webClient;

    public ChatGptService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com/v1").build();
    }

    public String summarizeSummaries(List<String> summaries) {
        String fullText = String.join("\n", summaries);

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "너는 계약서 내용의 유불리를 판단하는 법률 도우미야."),
                        Map.of("role", "user", "content", "아래 계약서 요약을 읽고 유리한 부분과 불리한 부분을 판단하여 따로따로 정리해 줘:\n" + fullText)
                ),
                "temperature", 0.5,
                "max_tokens", 2000
        );

        Map response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) return "요약 결과가 없습니다.";

        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return "요약 결과가 없습니다.";

        var message = (Map<String, Object>) choices.get(0).get("message");
        String content = message != null ? (String) message.get("content") : "요약 결과가 없습니다.";

        System.out.println("chatGptEvaluation 길이: " + content.length());
        System.out.println("chatGptEvaluation 전체 내용 (부분 출력):\n" + content.substring(0, Math.min(1000, content.length())));

        return content;
    }
}
