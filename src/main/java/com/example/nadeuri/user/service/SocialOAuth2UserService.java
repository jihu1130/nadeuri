package com.example.nadeuri.user.service;

import com.example.nadeuri.global.config.SecurityConfig;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SocialProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 소셜 로그인(카카오, 구글) 후 호출된다.
 * 1) Spring Security 기본 구현이 각 서비스의 사용자 정보 API를 호출하고
 * 2) 응답을 SocialProfile로 해석해 회원 가입/갱신한 뒤
 * 3) 세션에 저장할 LoginUser(우리 회원 ID 포함)를 돌려준다.
 *
 * 구글은 openid 범위를 요청하지 않으므로 OIDC(OidcUserService)가 아니라 이 서비스로 들어온다.
 */
@Service
@RequiredArgsConstructor
public class SocialOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User socialUser = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        SocialProfile profile;
        try {
            profile = toProfile(registrationId, socialUser.getAttributes());
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), e.getMessage(), e);
        }
        User user = userService.loginWithSocial(profile);
        return new LoginUser(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }

    static SocialProfile toProfile(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case SecurityConfig.KAKAO -> SocialProfile.kakao(attributes);
            case SecurityConfig.GOOGLE -> SocialProfile.google(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다: " + registrationId);
        };
    }
}
