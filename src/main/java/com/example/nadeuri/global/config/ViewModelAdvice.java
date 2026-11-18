package com.example.nadeuri.global.config;

import com.example.nadeuri.global.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 Thymeleaf 화면에서 공통으로 쓰는 값 */
@ControllerAdvice
public class ViewModelAdvice {

    private final String kakaoJsKey;

    public ViewModelAdvice(@Value("${kakao.javascript-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
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
