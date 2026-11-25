package com.example.nadeuri.global.config;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 로그인 없이 둘러보기는 되고, 모임 만들기·참여는 로그인 화면으로 보내는지 확인 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class SecurityConfigTest {

    @Autowired MockMvc mvc;
    @Autowired GatheringService service;

    private Gathering gathering() {
        return service.create(new GatheringCreateRequest("테스트", null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 5), 999L);
    }

    @Test
    void 로그인_없이_둘러볼_수_있다() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/gatherings/" + gathering().getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인하고 참여하기")));
        mvc.perform(get("/api/gatherings").param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        // 화면에서 쓰는 CSS·JS 파일은 로그인 없이 받아야 한다 (막히면 로그인 화면 HTML이 대신 내려옴)
        mvc.perform(get("/css/select-menu.css")).andExpect(status().isOk());
        mvc.perform(get("/js/select-menu.js")).andExpect(status().isOk());
        mvc.perform(get("/images/kakao-talk.png")).andExpect(status().isOk());
    }

    @Test
    void 로그인_안_하면_상단에_로그인과_회원가입_버튼이_보인다() throws Exception {
        for (String page : new String[]{"/", "/login", "/signup"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("class=\"topbar"),
                            org.hamcrest.Matchers.containsString("href=\"/signup\" class=\"btn btn-sm\""))));
        }
    }

    @Test
    void 로그인_안_하면_모임_만들기와_참여는_로그인_화면으로_간다() throws Exception {
        mvc.perform(get("/gatherings/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/gatherings/" + gathering().getPublicId() + "/join").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void CSRF_토큰_없는_POST는_막힌다() throws Exception {
        mvc.perform(post("/gatherings/" + gathering().getPublicId() + "/join")
                        .with(oauth2Login().oauth2User(new LoginUser(1L, "테스트", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인하면_참여되고_닉네임이_보인다() throws Exception {
        Gathering g = gathering();
        LoginUser me = new LoginUser(4242L, "산책왕", null);

        mvc.perform(post("/gatherings/" + g.getPublicId() + "/join").with(oauth2Login().oauth2User(me)).with(csrf()))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()));

        assertThat(service.hasJoined(g.getId(), 4242L)).isTrue();
        mvc.perform(get("/gatherings/" + g.getPublicId()).with(oauth2Login().oauth2User(me)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("산책왕")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("참여 완료")));
    }
}
