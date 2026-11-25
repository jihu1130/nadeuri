package com.example.nadeuri.assistant;

import com.example.nadeuri.assistant.client.GeminiClient;
import com.example.nadeuri.assistant.dto.AssistantRequest;
import com.example.nadeuri.assistant.exception.AssistantRateLimitedException;
import com.example.nadeuri.assistant.exception.AssistantUnavailableException;
import com.example.nadeuri.assistant.service.AssistantRateLimiter;
import com.example.nadeuri.assistant.service.AssistantService;
import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AI 도우미: API 접근 규칙, Gemini에 보내는 자료, 횟수 제한. Gemini는 가짜(MockBean)로 바꿔 실제로 부르지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AssistantTest {

    @Autowired MockMvc mvc;
    @Autowired AssistantService assistantService;
    @Autowired GatheringService gatheringService;
    @MockBean GeminiClient gemini;

    private static final AtomicLong USER_IDS = new AtomicLong(960_000L);

    private static String body(String question) {
        return """
                {"messages":[{"role":"user","text":"%s"}],"lat":37.5,"lng":127.0}""".formatted(question);
    }

    private LoginUser user() {
        return new LoginUser(USER_IDS.incrementAndGet(), "테스트회원", null);
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(post("/api/assistant/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("안녕")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gemini);
    }

    @Test
    void 로그인하면_답을_받는다() throws Exception {
        when(gemini.generate(anyString(), anyString())).thenReturn("주말에 산책 모임이 있어요.");
        mvc.perform(post("/api/assistant/chat").with(csrf()).with(oauth2Login().oauth2User(user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("주말 모임 있어?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("주말에 산책 모임이 있어요."));
    }

    @Test
    void CSRF_토큰_없으면_403() throws Exception {
        mvc.perform(post("/api/assistant/chat").with(oauth2Login().oauth2User(user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("안녕")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 너무_긴_질문과_빈_질문은_400() throws Exception {
        mvc.perform(post("/api/assistant/chat").with(csrf()).with(oauth2Login().oauth2User(user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/assistant/chat").with(csrf()).with(oauth2Login().oauth2User(user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }

    @Test
    void Gemini가_실패하면_503과_안내_문구() throws Exception {
        when(gemini.generate(anyString(), anyString()))
                .thenThrow(new AssistantUnavailableException("지금 AI 사용량이 많아요. 잠시 후 다시 물어봐 주세요."));
        mvc.perform(post("/api/assistant/chat").with(csrf()).with(oauth2Login().oauth2User(user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("안녕")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("사용량")));
    }

    @Test
    void 근처_모임과_대화를_자료로_보내고_회원_정보는_보내지_않는다() {
        Gathering g = gatheringService.create(GatheringCreateRequest.of("한강 저녁 산책", null, ActivityTag.WALK, "여의나루역 2번 출구",
                37.5271, 126.9326, Times.now().plusDays(1), 120, 6), USER_IDS.incrementAndGet());
        when(gemini.generate(anyString(), anyString())).thenReturn("답");

        LoginUser me = new LoginUser(USER_IDS.incrementAndGet(), "비밀닉네임", null);
        assistantService.ask(me.getId(), new AssistantRequest(List.of(
                new AssistantRequest.Message("user", "근처 산책 모임 알려줘"),
                new AssistantRequest.Message("assistant", "어느 날이 좋으세요?"),
                new AssistantRequest.Message("user", "내일")), 37.5271, 126.9326));

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(anyString(), input.capture());
        assertThat(input.getValue())
                .contains("한강 저녁 산책", "여의나루역 2번 출구", "/gatherings/" + g.getPublicId(), "1/6명", "모집 중")
                .contains("user: 근처 산책 모임 알려줘", "assistant: 어느 날이 좋으세요?", "user: 내일")
                .doesNotContain("비밀닉네임");
    }

    @Test
    void 마지막이_질문이_아니면_거부() {
        assertThatThrownBy(() -> assistantService.ask(1L, new AssistantRequest(
                List.of(new AssistantRequest.Message("assistant", "안녕하세요")), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 일분에_정해진_횟수를_넘으면_막는다() {
        AssistantRateLimiter limiter = new AssistantRateLimiter();
        for (int i = 0; i < AssistantRateLimiter.PER_MINUTE; i++) {
            limiter.acquire(7L);
        }
        assertThatThrownBy(() -> limiter.acquire(7L)).isInstanceOf(AssistantRateLimitedException.class);
        limiter.acquire(8L); // 다른 사람은 상관없음
    }
}
