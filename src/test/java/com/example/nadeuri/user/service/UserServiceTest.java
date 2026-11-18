package com.example.nadeuri.user.service;

import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.KakaoProfile;
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
        User first = userService.loginWithKakao(new KakaoProfile(kakaoId, "나들이", null));
        User second = userService.loginWithKakao(new KakaoProfile(kakaoId, "새닉네임", "https://img/p.jpg"));

        assertThat(second.getId()).isEqualTo(first.getId());
        User saved = userRepository.findById(first.getId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("새닉네임");
        assertThat(saved.getProfileImageUrl()).isEqualTo("https://img/p.jpg");
    }

    @Test
    void 카카오_응답에서_닉네임과_사진을_꺼낸다() {
        KakaoProfile p = KakaoProfile.from(Map.of(
                "id", 12345L,
                "kakao_account", Map.of("profile", Map.of(
                        "nickname", "산책왕", "profile_image_url", "https://k.kakaocdn.net/a.jpg"))));
        assertThat(p.id()).isEqualTo("12345");
        assertThat(p.nickname()).isEqualTo("산책왕");
        assertThat(p.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/a.jpg");
    }

    @Test
    void 닉네임_동의가_없으면_기본_닉네임을_쓴다() {
        KakaoProfile p = KakaoProfile.from(Map.of("id", 1L, "properties", Map.of()));
        assertThat(p.nickname()).isEqualTo(KakaoProfile.DEFAULT_NICKNAME);
        assertThat(p.profileImageUrl()).isNull();
    }

    @Test
    void 회원번호가_없으면_거부한다() {
        assertThatThrownBy(() -> KakaoProfile.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
