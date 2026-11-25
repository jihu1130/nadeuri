package com.example.nadeuri.user.service;

import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SocialProfile;
import com.example.nadeuri.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("h2")
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    @Test
    void 처음_로그인하면_가입되고_다시_로그인하면_프로필만_갱신된다() {
        String kakaoId = "kakao-" + System.nanoTime();
        User first = userService.loginWithSocial(new SocialProfile(User.PROVIDER_KAKAO, kakaoId, "나들이", null));
        User second = userService.loginWithSocial(
                new SocialProfile(User.PROVIDER_KAKAO, kakaoId, "새닉네임", "https://img/p.jpg"));

        assertThat(second.getId()).isEqualTo(first.getId());
        User saved = userRepository.findById(first.getId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("새닉네임");
        assertThat(saved.getProfileImageUrl()).isEqualTo("https://img/p.jpg");
    }

    @Test
    void 같은_번호라도_카카오와_구글은_다른_회원이다() {
        String sameId = "same-" + System.nanoTime();
        User kakao = userService.loginWithSocial(new SocialProfile(User.PROVIDER_KAKAO, sameId, "카카오", null));
        User google = userService.loginWithSocial(new SocialProfile(User.PROVIDER_GOOGLE, sameId, "구글", null));

        assertThat(google.getId()).isNotEqualTo(kakao.getId());
        assertThat(google.getProvider()).isEqualTo("google");
    }

    @Test
    void 카카오_응답에서_닉네임과_사진을_꺼낸다() {
        SocialProfile p = SocialProfile.kakao(Map.of(
                "id", 12345L,
                "kakao_account", Map.of("profile", Map.of(
                        "nickname", "산책왕", "profile_image_url", "https://k.kakaocdn.net/a.jpg"))));
        assertThat(p.provider()).isEqualTo(User.PROVIDER_KAKAO);
        assertThat(p.providerId()).isEqualTo("12345");
        assertThat(p.nickname()).isEqualTo("산책왕");
        assertThat(p.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/a.jpg");
    }

    @Test
    void 닉네임_동의가_없으면_기본_닉네임을_쓴다() {
        SocialProfile p = SocialProfile.kakao(Map.of("id", 1L, "properties", Map.of()));
        assertThat(p.nickname()).isEqualTo(SocialProfile.DEFAULT_NICKNAME);
        assertThat(p.profileImageUrl()).isNull();
    }

    @Test
    void 회원번호가_없으면_거부한다() {
        assertThatThrownBy(() -> SocialProfile.kakao(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialProfile.google(Map.of("name", "이름만")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 구글_응답에서_sub와_이름과_사진을_꺼낸다() {
        SocialProfile p = SocialProfile.google(Map.of(
                "sub", "109876543210987654321",
                "name", "  나들이 구글  ",
                "picture", "https://lh3.googleusercontent.com/a/abc"));
        assertThat(p.provider()).isEqualTo(User.PROVIDER_GOOGLE);
        assertThat(p.providerId()).isEqualTo("109876543210987654321");
        assertThat(p.nickname()).isEqualTo("나들이 구글");
        assertThat(p.profileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/a/abc");
    }

    @Test
    void 구글_이름이_없으면_기본_닉네임_너무_긴_사진_주소는_버린다() {
        SocialProfile p = SocialProfile.google(Map.of(
                "sub", "1", "picture", "https://x/" + "a".repeat(600)));
        assertThat(p.nickname()).isEqualTo(SocialProfile.DEFAULT_NICKNAME);
        assertThat(p.profileImageUrl()).isNull();
    }

    @Test
    void 지원하지_않는_로그인_방식은_거부한다() {
        assertThatThrownBy(() -> SocialOAuth2UserService.toProfile("naver", Map.of("id", 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SocialOAuth2UserService.toProfile("google", Map.of("sub", "7")).provider())
                .isEqualTo(User.PROVIDER_GOOGLE);
    }
}
