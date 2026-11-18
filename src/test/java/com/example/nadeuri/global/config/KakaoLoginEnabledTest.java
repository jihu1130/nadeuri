package com.example.nadeuri.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** REST API 키가 있으면 카카오 로그인 버튼이 보이고, 누르면 카카오 인증 화면으로 이동한다 */
@SpringBootTest(properties = {"kakao.rest-api-key=test-rest-key", "kakao.client-secret=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class KakaoLoginEnabledTest {

    @Autowired MockMvc mvc;

    @Test
    void 로그인_화면에_카카오_버튼이_보인다() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/kakao")));
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
}
