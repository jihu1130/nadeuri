package com.example.nadeuri.user.dto;

import com.example.nadeuri.user.domain.User;

import java.util.Map;

/**
 * 소셜 로그인(카카오, 구글) 사용자 정보에서 필요한 값만 꺼낸 것.
 * provider는 User.PROVIDER_KAKAO / User.PROVIDER_GOOGLE, providerId는 그 서비스의 회원 식별값.
 * 닉네임 동의를 안 했으면 값이 없을 수 있어 기본 닉네임을 쓴다.
 */
public record SocialProfile(String provider, String providerId, String nickname, String profileImageUrl) {

    public static final String DEFAULT_NICKNAME = "나들이 친구";
    private static final int MAX_NICKNAME = 50;
    private static final int MAX_IMAGE_URL = 500;
    private static final int MAX_PROVIDER_ID = 64;

    /** 닉네임·사진 주소를 DB 칸 길이에 맞게 정리한다 */
    public SocialProfile {
        if (providerId == null || providerId.isBlank() || providerId.length() > MAX_PROVIDER_ID) {
            throw new IllegalArgumentException("회원 식별값이 없거나 올바르지 않습니다.");
        }
        nickname = nickname == null || nickname.isBlank() ? DEFAULT_NICKNAME : truncate(nickname.strip(), MAX_NICKNAME);
        profileImageUrl = profileImageUrl == null || profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_IMAGE_URL ? null : profileImageUrl.strip();
    }

    /**
     * 카카오 사용자 정보 API(/v2/user/me) 응답.
     * <pre>
     * { "id": 123,
     *   "kakao_account": { "profile": { "nickname": "...", "profile_image_url": "..." } },
     *   "properties":    { "nickname": "...", "profile_image": "..." } }
     * </pre>
     */
    public static SocialProfile kakao(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null) {
            throw new IllegalArgumentException("카카오 회원번호가 없습니다.");
        }
        Map<?, ?> account = asMap(attributes.get("kakao_account"));
        Map<?, ?> profile = asMap(account.get("profile"));
        Map<?, ?> properties = asMap(attributes.get("properties"));
        return new SocialProfile(User.PROVIDER_KAKAO, String.valueOf(id),
                firstText(profile.get("nickname"), properties.get("nickname")),
                firstText(profile.get("profile_image_url"), properties.get("profile_image")));
    }

    /**
     * 구글 사용자 정보 API(https://www.googleapis.com/oauth2/v3/userinfo, profile 범위) 응답.
     * <pre>
     * { "sub": "1098...", "name": "홍길동", "given_name": "길동", "picture": "https://lh3.googleusercontent.com/..." }
     * </pre>
     * sub는 구글 계정마다 바뀌지 않는 번호다 (이메일은 바뀔 수 있어 쓰지 않는다).
     */
    public static SocialProfile google(Map<String, Object> attributes) {
        Object sub = attributes.get("sub");
        if (!(sub instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("구글 회원번호가 없습니다.");
        }
        return new SocialProfile(User.PROVIDER_GOOGLE, s,
                firstText(attributes.get("name"), attributes.get("given_name")),
                firstText(attributes.get("picture")));
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String firstText(Object... values) {
        for (Object v : values) {
            if (v instanceof String s && !s.isBlank()) return s.strip();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
