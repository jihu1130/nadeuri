package com.example.nadeuri.user.dto;

import java.util.Map;

/**
 * 카카오 사용자 정보 API(/v2/user/me) 응답에서 필요한 값만 꺼낸 것.
 * <pre>
 * { "id": 123,
 *   "kakao_account": { "profile": { "nickname": "...", "profile_image_url": "..." } },
 *   "properties":    { "nickname": "...", "profile_image": "..." } }
 * </pre>
 * 닉네임 동의를 안 했으면 값이 없을 수 있어 기본 닉네임을 쓴다.
 */
public record KakaoProfile(String id, String nickname, String profileImageUrl) {

    public static final String DEFAULT_NICKNAME = "나들이 친구";
    private static final int MAX_NICKNAME = 50;
    private static final int MAX_IMAGE_URL = 500;

    public static KakaoProfile from(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null) {
            throw new IllegalArgumentException("카카오 회원번호가 없습니다.");
        }
        Map<?, ?> account = asMap(attributes.get("kakao_account"));
        Map<?, ?> profile = asMap(account.get("profile"));
        Map<?, ?> properties = asMap(attributes.get("properties"));

        String nickname = firstText(profile.get("nickname"), properties.get("nickname"));
        String image = firstText(profile.get("profile_image_url"), properties.get("profile_image"));

        return new KakaoProfile(String.valueOf(id),
                nickname == null ? DEFAULT_NICKNAME : truncate(nickname, MAX_NICKNAME),
                image == null || image.length() > MAX_IMAGE_URL ? null : image);
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
