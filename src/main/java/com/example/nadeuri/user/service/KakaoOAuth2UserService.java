package com.example.nadeuri.user.service;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.KakaoProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 카카오 로그인 후 호출된다.
 * 1) Spring Security 기본 구현이 카카오 사용자 정보 API를 호출하고
 * 2) 응답을 KakaoProfile로 해석해 회원 가입/갱신한 뒤
 * 3) 세션에 저장할 LoginUser(우리 회원 ID 포함)를 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = super.loadUser(request);
        KakaoProfile profile;
        try {
            profile = KakaoProfile.from(kakaoUser.getAttributes());
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), e.getMessage(), e);
        }
        User user = userService.loginWithKakao(profile);
        return new LoginUser(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
