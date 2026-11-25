package com.example.nadeuri.global.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 키가 설정된 소셜 로그인만 버튼이 보이고, 누르면 그 서비스의 인증 화면으로 이동한다 */
class SocialLoginEnabledTest {

    @Nested
    @SpringBootTest(properties = {
            "kakao.rest-api-key=test-rest-key", "kakao.client-secret=test-secret",
            "google.client-id=test-google-id", "google.client-secret=test-google-secret"})
    @AutoConfigureMockMvc
    @ActiveProfiles("h2")
    class 카카오와_구글_모두 {

        @Autowired MockMvc mvc;

        @Test
        void 로그인_화면에_두_버튼이_보인다() throws Exception {
            mvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("/oauth2/authorization/kakao"),
                            containsString("/oauth2/authorization/google"))));
        }

        @Test
        void 카카오_인증_화면으로_보낸다() throws Exception {
            mvc.perform(get("/oauth2/authorization/kakao"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", allOf(
                            containsString("https://kauth.kakao.com/oauth/authorize"),
                            containsString("client_id=test-rest-key"),
                            containsString("redirect_uri=http://localhost/login/oauth2/code/kakao"))));
        }

        @Test
        void 구글_인증_화면으로_보낸다_이메일과_openid는_요청하지_않는다() throws Exception {
            mvc.perform(get("/oauth2/authorization/google"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", allOf(
                            containsString("https://accounts.google.com/o/oauth2/v2/auth"),
                            containsString("client_id=test-google-id"),
                            containsString("scope=profile"),
                            not(containsString("openid")),
                            not(containsString("email")),
                            containsString("redirect_uri=http://localhost/login/oauth2/code/google"))));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "kakao.rest-api-key=", "google.client-id=test-google-id", "google.client-secret=test-google-secret"})
    @AutoConfigureMockMvc
    @ActiveProfiles("h2")
    class 구글만 {

        @Autowired MockMvc mvc;

        @Test
        void 구글_버튼만_보인다() throws Exception {
            mvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("/oauth2/authorization/google"),
                            not(containsString("/oauth2/authorization/kakao")))));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "kakao.rest-api-key=test-rest-key", "kakao.client-secret=test-secret",
            "google.client-id=test-google-id", "google.client-secret="})
    @AutoConfigureMockMvc
    @ActiveProfiles("h2")
    class 구글_시크릿이_비면 {

        @Autowired MockMvc mvc;

        @Test
        void 구글_버튼은_꺼진다() throws Exception {
            mvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("/oauth2/authorization/kakao"),
                            not(containsString("/oauth2/authorization/google")))));
        }
    }
}
