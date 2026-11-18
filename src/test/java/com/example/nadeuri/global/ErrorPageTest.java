package com.example.nadeuri.global;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 화면. MockMvc는 /error 로 넘기는 과정을 흉내 내지 않아서 실제 서버를 띄워 확인한다.
 * 브라우저(HTML 요청)는 한국어 오류 화면, API(JSON 요청)는 JSON 오류를 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class ErrorPageTest {

    @Autowired TestRestTemplate rest;

    private ResponseEntity<String> getHtml(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.TEXT_HTML));
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void 없는_모임은_404_한국어_화면() {
        ResponseEntity<String> r = getHtml("/gatherings/987654321");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody()).contains("찾는 모임이나 페이지가 없어요").contains("홈으로 가기")
                .doesNotContain("Whitelabel").doesNotContain("Exception");
    }

    /**
     * 누구나 볼 수 있는 경로 아래의 없는 파일은 404 화면.
     * (그 밖의 모르는 주소는 "로그인 필요"로 분류되어, 로그인 전에는 로그인 화면으로 간다)
     */
    @Test
    void 공개_경로의_없는_파일도_404_한국어_화면() {
        ResponseEntity<String> r = getHtml("/css/no-such-file.css");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody()).contains("찾는 모임이나 페이지가 없어요");
    }

    @Test
    void 잘못된_주소_형식은_400_한국어_화면() {
        ResponseEntity<String> r = getHtml("/gatherings/abc");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).contains("요청을 처리할 수 없어요").doesNotContain("Whitelabel");
    }

    @Test
    void API는_JSON_오류를_받는다() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> r = rest.exchange("/api/gatherings?lat=999&lng=127", HttpMethod.GET,
                new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(r.getBody()).contains("\"status\":400").doesNotContain("<html");
    }
}
