package com.consentradar.consentradar.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final boolean enabled;

    public LlmClient(@Value("${llm.api-key:}") String apiKey,
                     @Value("${llm.model:gpt-4o-mini}") String model,
                     @Value("${llm.base-url:https://api.openai.com/v1/chat/completions}") String baseUrl,
                     @Value("${llm.enabled:false}") boolean enabled) {
        this(apiKey, model, baseUrl, enabled, HttpClient.newHttpClient(), new ObjectMapper());
    }

    LlmClient(String apiKey,
              String model,
              String baseUrl,
              boolean enabled,
              HttpClient httpClient,
              ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 완성된 프롬프트를 받아 LLM을 호출하고 원시 응답 문자열을 반환한다.
     * LlmRetryModule.LlmCaller 인터페이스와 호환되는 시그니처.
     */
    public String callWithPrompt(String prompt) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return mockResponse();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "model", model,
                            "messages", List.of(Map.of(
                                    "role", "user",
                                    "content", prompt
                            )),
                            "temperature", 0.2
                    ))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("LLM API 호출 실패: " + response.statusCode() + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }
            throw new IllegalStateException("LLM 응답 형식이 올바르지 않습니다: " + response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM API 호출 중 오류가 발생했습니다.", e);
        }
    }

    private String mockResponse() {
        return """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    {
                      "itemName": "서비스 이용을 위한 필수 개인정보 수집",
                      "itemType": "REQUIRED",
                      "ds": "HIGH",
                      "es": "MEDIUM",
                      "tf": "LONG",
                      "pc": "COMPLIANT",
                      "ai": "LOW_RISK",
                      "dsReason": "이름, 휴대폰번호, 이메일 등 민감한 식별 정보를 수집함",
                      "esReason": "서비스 내부 및 계열사 일부 공유",
                      "tfReason": "회원 탈퇴 후에도 법적 의무 보관 기간(5년) 적용",
                      "pcReason": "수집 목적이 약관에 명확히 기재되어 있음",
                      "aiReason": "자동화 의사결정에 활용되지 않음"
                    },
                    {
                      "itemName": "마케팅 정보 수신 동의",
                      "itemType": "OPTIONAL",
                      "ds": "MODERATE",
                      "es": "HIGH",
                      "tf": "LONG",
                      "pc": "NON_COMPLIANT",
                      "ai": "HIGH_RISK",
                      "dsReason": "구매 이력, 관심사 등 행동 데이터 포함",
                      "esReason": "제3자 광고 파트너사에 폭넓게 공유됨",
                      "tfReason": "동의 철회 전까지 무기한 보관",
                      "pcReason": "마케팅 활용 범위가 포괄적으로만 기재되어 GDPR Article 5 위반 소지",
                      "aiReason": "개인화 광고 타겟팅 알고리즘에 활용됨"
                    }
                  ]
                }
                """;
    }
}
