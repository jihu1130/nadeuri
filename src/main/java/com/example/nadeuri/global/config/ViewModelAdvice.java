package com.example.nadeuri.global.config;

import com.example.nadeuri.global.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 Thymeleaf 화면에서 공통으로 쓰는 값 */
@ControllerAdvice
public class ViewModelAdvice {

    private final String kakaoJsKey;
    private final boolean assistantEnabled;

    public ViewModelAdvice(@Value("${kakao.javascript-key:}") String kakaoJsKey,
                           @Value("${gemini.api-key:}") String geminiApiKey) {
        this.kakaoJsKey = kakaoJsKey;
        this.assistantEnabled = geminiApiKey != null && !geminiApiKey.isBlank();
    }

    /**
     * fetch로 POST할 때 쓰는 CSRF 토큰 (AI 도우미). 화면을 그리기 전에 미리 꺼내 둔다:
     * 토큰은 처음 읽을 때 세션을 만드는데, 긴 화면 끝부분에서 처음 읽으면 응답이 이미 나가기 시작해 세션을 못 만든다.
     */
    @ModelAttribute("csrfToken")
    public CsrfToken csrfToken(CsrfToken token) {
        if (!assistantEnabled || token == null) {
            return null; // 도우미가 꺼져 있으면 쓸 곳이 없으니 세션도 만들지 않는다
        }
        token.getToken();
        return token;
    }

    /** AI 도우미(Gemini) 키가 설정돼 있으면 화면 오른쪽 아래에 도우미 버튼을 띄운다 */
    @ModelAttribute("assistantEnabled")
    public boolean assistantEnabled() {
        return assistantEnabled;
    }

    /** 카카오맵 SDK 로드용 JavaScript 키 (공개용 키, 등록된 도메인에서만 동작) */
    @ModelAttribute("kakaoJsKey")
    public String kakaoJsKey() {
        return kakaoJsKey;
    }

    /** 로그인한 사용자 (로그인 안 했으면 null) — 상단바의 닉네임/로그아웃 표시용 */
    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser user) {
        return user;
    }
}
