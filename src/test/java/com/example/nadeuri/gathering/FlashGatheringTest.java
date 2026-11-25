package com.example.nadeuri.gathering;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 번개 모임: 2시간 안에 시작해야 하고, 지도 API·상세 화면에 번개로 표시된다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class FlashGatheringTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final DateTimeFormatter MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    /** 다른 테스트의 모임과 섞이지 않는 좌표 */
    private static final String LAT = "-41.3", LNG = "174.8";

    @Autowired MockMvc mvc;
    @Autowired GatheringService service;
    @Autowired UserService userService;

    private User user() {
        String username = "f" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "번개" + SEQ.get()));
    }

    private static String inMinutes(int m) {
        return Times.now().plusMinutes(m).format(MIN);
    }

    private static MockHttpServletRequestBuilder form(String url, User u, String start, boolean flash) {
        var req = post(url).with(oauth2Login().oauth2User(new LoginUser(u.getId(), u.getNickname(), null))).with(csrf())
                .param("title", "번개 테스트").param("tag", "CAFE").param("placeName", "카페")
                .param("latitude", LAT).param("longitude", LNG)
                .param("startAt", start).param("durationMinutes", "60").param("maxPeople", "4");
        return flash ? req.param("flash", "true") : req;
    }

    @Test
    void 번개_모임은_2시간_안에_시작하면_만들어진다() throws Exception {
        User host = user();
        mvc.perform(form("/gatherings", host, inMinutes(30), true)).andExpect(status().is3xxRedirection());

        Gathering g = service.hostedBy(host.getId()).get(0);
        assertThat(g.isFlash()).isTrue();

        // 지도 API에 flash로, 상세 화면에 배지로 나온다
        mvc.perform(get("/api/gatherings").param("lat", LAT).param("lng", LNG).param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + g.getPublicId() + "')].flash").value(true));
        mvc.perform(get("/gatherings/" + g.getPublicId()))
                .andExpect(content().string(containsString("⚡ 번개")));
    }

    @Test
    void 번개_모임인데_2시간보다_뒤에_시작하면_다시_입력받는다() throws Exception {
        User host = user();
        mvc.perform(form("/gatherings", host, inMinutes(5 * 60), true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("번개 모임은 지금부터 2시간 안에 시작해야 해요")))
                // 입력한 번개 선택이 그대로 남아 있다
                .andExpect(content().string(containsString("name=\"flash\" value=\"true\" checked=\"checked\"")));
        assertThat(service.hostedBy(host.getId())).isEmpty();
    }

    @Test
    void 번개를_고르지_않으면_일반_모임이고_먼_날짜도_된다() throws Exception {
        User host = user();
        mvc.perform(form("/gatherings", host, inMinutes(5 * 60), false)).andExpect(status().is3xxRedirection());
        assertThat(service.hostedBy(host.getId()).get(0).isFlash()).isFalse();
    }

    @Test
    void 수정하면서_번개를_켜고_끌_수_있다() throws Exception {
        User host = user();
        mvc.perform(form("/gatherings", host, inMinutes(5 * 60), false)).andExpect(status().is3xxRedirection());
        Gathering g = service.hostedBy(host.getId()).get(0);
        String edit = "/gatherings/" + g.getPublicId() + "/edit";

        // 시작이 5시간 뒤인 채로 번개를 켜면 안 된다
        mvc.perform(form(edit, host, inMinutes(5 * 60), true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2시간 안에 시작해야")));
        assertThat(service.get(g.getId()).isFlash()).isFalse();

        // 시작을 1시간 뒤로 당기면 켤 수 있고, 다시 끌 수도 있다
        mvc.perform(form(edit, host, inMinutes(60), true)).andExpect(status().is3xxRedirection());
        assertThat(service.get(g.getId()).isFlash()).isTrue();
        mvc.perform(form(edit, host, inMinutes(60), false)).andExpect(status().is3xxRedirection());
        assertThat(service.get(g.getId()).isFlash()).isFalse();
    }

    @Test
    void 요청을_코드로_만들면_기본은_일반_모임이다() {
        var req = GatheringCreateRequest.of("t", null, com.example.nadeuri.gathering.domain.ActivityTag.WALK, "p",
                37.5, 127.0, Times.now().plusHours(5), 60, 4);
        assertThat(req.isFlashOn()).isFalse();
        assertThat(req.isFlashStartSoon()).isTrue();
    }
}
