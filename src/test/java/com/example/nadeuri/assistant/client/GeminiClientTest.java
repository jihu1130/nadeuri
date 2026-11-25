package com.example.nadeuri.assistant.client;

import com.example.nadeuri.assistant.exception.AssistantUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini 응답에서 답 글자를 꺼내는 부분 (실제 호출 없음) */
class GeminiClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void output_text가_있으면_그것을_쓴다() throws Exception {
        assertThat(GeminiClient.extractText(json.readTree("{\"id\":\"x\",\"output_text\":\"안녕하세요\"}")))
                .isEqualTo("안녕하세요");
    }

    @Test
    void steps에서_마지막_모델_답을_꺼낸다_생각과_입력은_건너뛴다() throws Exception {
        String response = """
                {"id":"x","steps":[
                  {"type":"user_input","content":[{"type":"text","text":"질문"}]},
                  {"type":"thought","content":[{"type":"text","text":"생각 요약"}]},
                  {"type":"model_output","content":[{"type":"text","text":"첫 줄 "},{"type":"text","text":"둘째 줄"}]}
                ]}""";
        assertThat(GeminiClient.extractText(json.readTree(response))).isEqualTo("첫 줄 둘째 줄");
    }

    @Test
    void 답이_없으면_null() throws Exception {
        assertThat(GeminiClient.extractText(json.readTree("{\"steps\":[]}"))).isNull();
        assertThat(GeminiClient.extractText(null)).isNull();
    }

    @Test
    void 키가_없으면_부르지_않고_안내() {
        GeminiClient client = new GeminiClient("", "gemini-3.5-flash-lite", "http://localhost:1/never");
        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.generate("s", "i")).isInstanceOf(AssistantUnavailableException.class);
    }
}
