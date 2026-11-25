package com.example.nadeuri.assistant.client;

import com.example.nadeuri.assistant.exception.AssistantUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API (Interactions API, 무료 등급) 호출.
 * 대화 기록을 구글에 저장하지 않도록 store=false로 보내고, 매번 필요한 내용을 전부 담아 한 번만 부른다.
 * <pre>
 * POST https://generativelanguage.googleapis.com/v1beta/interactions
 * 헤더 x-goog-api-key: 키
 * { "model": "...", "system_instruction": "...", "input": "...", "store": false,
 *   "generation_config": { "max_output_tokens": 1024 } }
 * </pre>
 * 무료 등급은 입력 내용이 구글 서비스 개선에 쓰일 수 있으므로, 회원 이름·아이디 같은 개인정보는 넣지 않는다.
 */
@Slf4j
@Component
public class GeminiClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(40);
    private static final int MAX_OUTPUT_TOKENS = 1024;
    /** 문서 예시가 함께 보내는 API 버전 헤더 */
    private static final String API_REVISION = "2026-05-20";

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
                        @Value("${gemini.url:https://generativelanguage.googleapis.com/v1beta/interactions}") String url) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 시스템 지시문 + 입력 글을 보내고 모델의 답(글자)을 돌려준다.
     *
     * @throws AssistantUnavailableException 키 없음, 사용량 초과, 네트워크·서버 오류, 빈 답
     */
    public String generate(String systemInstruction, String input) {
        if (!isConfigured()) {
            throw new AssistantUnavailableException("AI 도우미가 아직 설정되지 않았어요.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("system_instruction", systemInstruction);
        body.put("input", input);
        body.put("store", false);
        body.put("generation_config", Map.of("max_output_tokens", MAX_OUTPUT_TOKENS));

        JsonNode response;
        try {
            response = http.post()
                    .header("x-goog-api-key", apiKey)
                    .header("Api-Revision", API_REVISION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.warn("Gemini 호출 실패: HTTP {} {}", status.value(), mask(truncate(e.getResponseBodyAsString())));
            if (status.value() == 429) {
                throw new AssistantUnavailableException("지금 AI 사용량이 많아요. 잠시 후 다시 물어봐 주세요.");
            }
            throw new AssistantUnavailableException("AI 도우미가 지금 응답하지 못했어요. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("Gemini 연결 실패: {}", mask(String.valueOf(e.getMessage())));
            throw new AssistantUnavailableException("AI 도우미에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }

        String text = extractText(response);
        if (text == null || text.isBlank()) {
            log.warn("Gemini 응답에 글자가 없음: {}", truncate(String.valueOf(response)));
            throw new AssistantUnavailableException("AI 도우미가 답을 만들지 못했어요. 질문을 바꿔서 다시 물어봐 주세요.");
        }
        return text.strip();
    }

    /**
     * 응답에서 모델이 쓴 글자를 꺼낸다.
     * output_text가 있으면 그것을, 없으면 steps를 뒤에서부터 보며 content 안의 text를 모은
     * 마지막 단계(모델 답)를 쓴다. 사용자 입력 단계(user_input)와 생각 단계(thought)는 건너뛴다.
     */
    static String extractText(JsonNode response) {
        if (response == null || response.isNull()) return null;
        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }
        JsonNode steps = response.get("steps");
        if (steps == null || !steps.isArray()) return null;
        for (int i = steps.size() - 1; i >= 0; i--) {
            JsonNode step = steps.get(i);
            String type = step.path("type").asText("");
            if (type.equals("user_input") || type.contains("thought")) continue;
            String text = textOf(step.get("content"));
            if (text != null && !text.isBlank()) return text;
        }
        return null;
    }

    private static String textOf(JsonNode content) {
        if (content == null) return null;
        if (content.isTextual()) return content.asText();
        if (!content.isArray()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonNode part : content) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual()) parts.add(text.asText());
        }
        return parts.isEmpty() ? null : String.join("", parts);
    }

    private String mask(String s) {
        return apiKey.isBlank() ? s : s.replace(apiKey, "***");
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }
}
