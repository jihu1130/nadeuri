package com.example.nadeuri.user.controller;

import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.exception.DuplicateUsernameException;
import com.example.nadeuri.user.repository.UserRepository;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 우리 사이트(아이디 + 비밀번호) 회원가입과 로그인 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class LocalLoginTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    /** 테스트끼리 겹치지 않는 아이디 (영문 시작, 20자 이하) */
    private static String uniqueUsername() {
        return "user" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
    }

    @Test
    void 가입하면_비밀번호는_해시로_저장되고_아이디는_소문자로_맞춘다() {
        String username = uniqueUsername();
        User u = userService.signUp(new SignUpRequest("  " + username.toUpperCase() + " ", "abcd1234", "abcd1234", "산책왕"));

        assertThat(u.getProviderId()).isEqualTo(username);
        assertThat(u.getPassword()).startsWith("{bcrypt}").doesNotContain("abcd1234");
        assertThatThrownBy(() -> userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "또가입")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void 가입_후_로그인하면_원래_가려던_곳으로_돌아간다() throws Exception {
        String username = uniqueUsername();
        mvc.perform(post("/signup").with(csrf())
                        .param("username", username).param("nickname", "러너")
                        .param("password", "runner123").param("passwordConfirm", "runner123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("username", username));

        // 로그인이 필요한 화면에 먼저 들어갔다가(세션에 저장됨) 로그인
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/gatherings/new").session(session)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username.toUpperCase()).param("password", "runner123"))
                .andExpect(authenticated().withUsername(
                        String.valueOf(userRepository.findByProviderAndProviderId("local", username).orElseThrow().getId())))
                .andExpect(redirectedUrlPattern("**/gatherings/new?continue"));

        mvc.perform(get("/").session(session))
                .andExpect(content().string(containsString("러너")));
    }

    @Test
    void 브라우저가_몰래_보낸_요청은_로그인_후_돌아갈_곳으로_기억하지_않는다() throws Exception {
        String username = uniqueUsername();
        userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "닉"));
        MockHttpSession session = new MockHttpSession();

        // 사용자가 연 화면 → 기억해야 함
        mvc.perform(get("/gatherings/new").session(session)).andExpect(redirectedUrlPattern("**/login"));
        // Chrome 개발자 도구가 자동으로 보내는 요청 → 로그인 화면으로 보내지 않고 그냥 404
        mvc.perform(get("/.well-known/appspecific/com.chrome.devtools.json").session(session))
                .andExpect(status().isNotFound());
        // 화면 이동이 아닌 fetch 요청 → 로그인 화면으로는 보내지만 돌아갈 곳으로 기억하지 않음
        mvc.perform(get("/gatherings/1/sign-in").header("X-Requested-With", "XMLHttpRequest").session(session))
                .andExpect(redirectedUrlPattern("**/login"));

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", "abcd1234"))
                .andExpect(redirectedUrlPattern("**/gatherings/new?continue"));
    }

    @Test
    void 돌아갈_곳이_없으면_로그인_후_홈으로_간다() throws Exception {
        String username = uniqueUsername();
        userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "닉"));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/.well-known/appspecific/com.chrome.devtools.json").session(session))
                .andExpect(status().isNotFound());

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", "abcd1234"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 비밀번호가_틀리면_로그인_화면에_오류를_보여준다() throws Exception {
        String username = uniqueUsername();
        userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "닉"));

        mvc.perform(formLogin("/login").user(username).password("wrong1234"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error=local"));
        mvc.perform(get("/login").param("error", "local"))
                .andExpect(content().string(containsString("아이디 또는 비밀번호가 맞지 않")));
    }

    @Test
    void 가입되지_않은_아이디도_같은_오류를_보여준다() throws Exception {
        mvc.perform(formLogin("/login").user(uniqueUsername()).password("abcd1234"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error=local"));
    }

    @Test
    void 가입_폼_검증_오류를_보여준다() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("username", "1ab").param("nickname", "")
                        .param("password", "short").param("passwordConfirm", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아이디는 영문으로 시작하고")))
                .andExpect(content().string(containsString("닉네임을 입력해 주세요")))
                .andExpect(content().string(containsString("비밀번호는 8~64자로 정해 주세요")))
                .andExpect(content().string(containsString("비밀번호가 서로 달라요")))
                // 입력한 비밀번호를 화면에 다시 내려보내지 않는다
                .andExpect(content().string(not(containsString("value=\"short\""))));
    }

    @Test
    void 아이디에_특수문자나_공백이_있으면_가입할_수_없다() throws Exception {
        for (String bad : new String[]{"hello world", "hi@there", "한글아이디", "abc"}) {
            mvc.perform(post("/signup").with(csrf())
                            .param("username", bad).param("nickname", "닉")
                            .param("password", "abcd1234").param("passwordConfirm", "abcd1234"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("아이디는 영문으로 시작하고")));
        }
    }

    @Test
    void 이미_사용_중인_아이디면_가입_폼에_알려준다() throws Exception {
        String username = uniqueUsername();
        userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "닉"));

        mvc.perform(post("/signup").with(csrf())
                        .param("username", username).param("nickname", "다른사람")
                        .param("password", "abcd1234").param("passwordConfirm", "abcd1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 사용 중인 아이디예요")));
    }
}
