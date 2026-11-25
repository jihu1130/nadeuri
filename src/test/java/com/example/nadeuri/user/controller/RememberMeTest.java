package com.example.nadeuri.user.controller;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.PasswordChangeRequest;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.repository.RememberMeTokenRepository;
import com.example.nadeuri.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 세션 로그인 + "로그인 상태 유지".
 * 세션 쿠키 없이 remember-me 쿠키만 들고 오면 = 세션이 끝난(브라우저를 닫은) 뒤 다시 온 상황.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RememberMeTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired RememberMeTokenRepository tokens;

    private User signUp(String username) {
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "기억해"));
    }

    private static String uniqueUsername() {
        return "remember" + (System.nanoTime() % 1_000_000L) + SEQ.incrementAndGet();
    }

    private MvcResult login(String username, boolean remember) throws Exception {
        var req = post("/login").with(csrf()).param("username", username).param("password", "abcd1234");
        if (remember) req.param("remember-me", "on");
        return mvc.perform(req).andExpect(authenticated()).andReturn();
    }

    @Test
    void 체크하지_않으면_세션만_쓰고_쿠키를_주지_않는다() throws Exception {
        User u = signUp(uniqueUsername());
        MvcResult r = login(u.getProviderId(), false);
        assertThat(r.getResponse().getCookie("remember-me")).isNull();
        assertThat(tokens.countByUserId(u.getId())).isZero();
    }

    @Test
    void 체크하면_14일_쿠키를_주고_세션이_끝나도_다시_로그인된다() throws Exception {
        User u = signUp(uniqueUsername());
        Cookie cookie = login(u.getProviderId(), true).getResponse().getCookie("remember-me");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo(14 * 24 * 3600);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(tokens.countByUserId(u.getId())).isEqualTo(1);

        // 세션 없이 쿠키만: 로그인 필요한 화면이 열리고, 쓴 쿠키는 새 값으로 바뀐다
        MvcResult again = mvc.perform(get("/gatherings/new").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(authenticated().withAuthentication(a ->
                        assertThat(((LoginUser) a.getPrincipal()).getId()).isEqualTo(u.getId())))
                .andReturn();
        Cookie renewed = again.getResponse().getCookie("remember-me");
        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotEqualTo(cookie.getValue());
    }

    @Test
    void 쿠키를_훔쳐_예전_값으로_오면_그_회원의_자동로그인을_모두_끊는다() throws Exception {
        User u = signUp(uniqueUsername());
        Cookie stolen = login(u.getProviderId(), true).getResponse().getCookie("remember-me");
        mvc.perform(get("/gatherings/new").cookie(stolen)).andExpect(authenticated());   // 진짜 주인이 먼저 씀 → 값 바뀜

        mvc.perform(get("/gatherings/new").cookie(stolen)).andExpect(unauthenticated());
        assertThat(tokens.countByUserId(u.getId())).isZero();
    }

    @Test
    void 로그아웃하면_쿠키와_DB_토큰이_지워진다() throws Exception {
        User u = signUp(uniqueUsername());
        MvcResult r = login(u.getProviderId(), true);
        Cookie cookie = r.getResponse().getCookie("remember-me");

        MvcResult out = mvc.perform(post("/logout").with(csrf()).cookie(cookie)
                        .session((org.springframework.mock.web.MockHttpSession) r.getRequest().getSession()))
                .andExpect(redirectedUrl("/")).andReturn();
        assertThat(out.getResponse().getCookie("remember-me").getMaxAge()).isZero();
        assertThat(tokens.countByUserId(u.getId())).isZero();
        mvc.perform(get("/gatherings/new").cookie(cookie)).andExpect(unauthenticated());
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기의_자동로그인도_끊긴다() throws Exception {
        User u = signUp(uniqueUsername());
        Cookie otherDevice = login(u.getProviderId(), true).getResponse().getCookie("remember-me");

        userService.changePassword(u.getId(), new PasswordChangeRequest("abcd1234", "efgh5678", "efgh5678"));

        assertThat(tokens.countByUserId(u.getId())).isZero();
        mvc.perform(get("/gatherings/new").cookie(otherDevice)).andExpect(unauthenticated());
    }

    @Test
    void 로그인_화면에_로그인_상태_유지_체크박스가_있다() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"remember-me\" type=\"checkbox\"")));
    }
}
