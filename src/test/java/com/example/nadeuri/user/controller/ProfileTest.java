package com.example.nadeuri.user.controller;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.dto.SocialProfile;
import com.example.nadeuri.user.repository.UserRepository;
import com.example.nadeuri.user.service.ProfileImageStorage;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 프로필 설정: 닉네임·자기소개, 사진 올리기·지우기, 비밀번호 바꾸기 */
@SpringBootTest(properties = "nadeuri.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ProfileTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired ProfileImageStorage storage;
    @Autowired PasswordEncoder passwordEncoder;

    private User local(String nickname) {
        String username = "p" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private User kakao(String nickname) {
        return userService.loginWithSocial(new SocialProfile(User.PROVIDER_KAKAO, "k" + System.nanoTime(), nickname,
                "https://k.kakaocdn.net/photo.jpg"));
    }

    private static LoginUser login(User u) {
        return new LoginUser(u.getId(), u.getNickname(), u.getProfileImageUrl());
    }

    private static byte[] png(int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void 프로필_설정은_로그인이_필요하다() throws Exception {
        mvc.perform(get("/me/profile")).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 닉네임과_자기소개를_바꾼다() throws Exception {
        User me = local("처음이름");
        mvc.perform(get("/me/profile").with(oauth2Login().oauth2User(login(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"처음이름\"")))
                .andExpect(content().string(containsString("비밀번호 바꾸기")));

        mvc.perform(post("/me/profile").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("nickname", "  새이름 ").param("bio", " 한강 산책 좋아해요 "))
                .andExpect(redirectedUrl("/me/profile"))
                .andExpect(flash().attribute("message", "프로필을 저장했어요."));
        User saved = userService.get(me.getId());
        assertThat(saved.getNickname()).isEqualTo("새이름");
        assertThat(saved.getBio()).isEqualTo("한강 산책 좋아해요");

        // 자기소개를 비우면 지운다
        mvc.perform(post("/me/profile").with(oauth2Login().oauth2User(login(me))).with(csrf())
                .param("nickname", "새이름").param("bio", "   "));
        assertThat(userService.get(me.getId()).getBio()).isNull();
    }

    @Test
    void 닉네임이_비거나_길면_다시_입력받는다() throws Exception {
        User me = local("그대로");
        mvc.perform(post("/me/profile").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("nickname", " ").param("bio", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("닉네임을 입력해 주세요.")));
        mvc.perform(post("/me/profile").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("nickname", "가".repeat(21)).param("bio", "나".repeat(101)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("닉네임은 20자까지")))
                .andExpect(content().string(containsString("자기소개는 100자까지")));
        assertThat(userService.get(me.getId()).getNickname()).isEqualTo("그대로");
    }

    @Test
    void 카카오_회원이_바꾼_닉네임과_사진은_다시_로그인해도_유지된다() throws Exception {
        User me = kakao("카카오이름");
        // 아직 안 바꿨으면 로그인 때마다 카카오 값으로 맞춘다
        userService.loginWithSocial(new SocialProfile(User.PROVIDER_KAKAO, me.getProviderId(), "카카오새이름", "https://k.kakaocdn.net/new.jpg"));
        assertThat(userService.get(me.getId()).getNickname()).isEqualTo("카카오새이름");

        mvc.perform(post("/me/profile").with(oauth2Login().oauth2User(login(me))).with(csrf())
                .param("nickname", "내가정한이름").param("bio", ""));
        mvc.perform(post("/me/profile/photo/delete").with(oauth2Login().oauth2User(login(me))).with(csrf()));

        userService.loginWithSocial(new SocialProfile(User.PROVIDER_KAKAO, me.getProviderId(), "카카오또새이름", "https://k.kakaocdn.net/again.jpg"));
        User after = userService.get(me.getId());
        assertThat(after.getNickname()).isEqualTo("내가정한이름");
        assertThat(after.getProfileImageUrl()).isNull();

        // 카카오 회원에게는 비밀번호 칸이 없고, 요청해도 바뀌지 않는다
        mvc.perform(get("/me/profile").with(oauth2Login().oauth2User(login(me))))
                .andExpect(content().string(not(containsString("비밀번호 바꾸기"))))
                .andExpect(content().string(containsString("카카오")));
        mvc.perform(post("/me/password").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("currentPassword", "x").param("newPassword", "abcd5678").param("newPasswordConfirm", "abcd5678"))
                .andExpect(redirectedUrl("/me/profile"))
                .andExpect(flash().attribute("message", containsString("비밀번호가 없어요")));
    }

    @Test
    void 사진을_올리면_정사각형으로_저장되고_누구나_볼_수_있고_지우면_파일도_지운다() throws Exception {
        User me = local("사진");
        var file = new MockMultipartFile("photo", "wide.png", "image/png", png(900, 300, false));
        mvc.perform(multipart("/me/profile/photo").file(file).with(oauth2Login().oauth2User(login(me))).with(csrf()))
                .andExpect(redirectedUrl("/me/profile"))
                .andExpect(flash().attribute("message", "프로필 사진을 바꿨어요."));

        String url = userService.get(me.getId()).getProfileImageUrl();
        assertThat(url).startsWith(ProfileImageStorage.URL_PREFIX).endsWith(".jpg");
        var path = storage.directory().resolve(url.substring(ProfileImageStorage.URL_PREFIX.length()));
        BufferedImage stored = ImageIO.read(path.toFile());
        assertThat(stored.getWidth()).isEqualTo(300);
        assertThat(stored.getHeight()).isEqualTo(300);
        // 로그인하지 않아도 사진은 보인다 (참여자 목록)
        mvc.perform(get(url)).andExpect(status().isOk());

        // 사진을 바꾸면 예전 파일은 지운다
        var second = new MockMultipartFile("photo", "big.png", "image/png", png(1000, 1200, true));
        mvc.perform(multipart("/me/profile/photo").file(second).with(oauth2Login().oauth2User(login(me))).with(csrf()));
        String url2 = userService.get(me.getId()).getProfileImageUrl();
        assertThat(url2).endsWith(".png");
        assertThat(Files.exists(path)).isFalse();
        var path2 = storage.directory().resolve(url2.substring(ProfileImageStorage.URL_PREFIX.length()));
        assertThat(ImageIO.read(path2.toFile()).getWidth()).isEqualTo(320);

        mvc.perform(post("/me/profile/photo/delete").with(oauth2Login().oauth2User(login(me))).with(csrf()))
                .andExpect(flash().attribute("message", "프로필 사진을 지웠어요."));
        assertThat(userService.get(me.getId()).getProfileImageUrl()).isNull();
        assertThat(Files.exists(path2)).isFalse();
    }

    @Test
    void 그림이_아니거나_비었으면_사진을_바꾸지_않는다() throws Exception {
        User me = local("가짜사진");
        var fake = new MockMultipartFile("photo", "virus.jpg", "image/jpeg", "not an image".getBytes());
        mvc.perform(multipart("/me/profile/photo").file(fake).with(oauth2Login().oauth2User(login(me))).with(csrf()))
                .andExpect(redirectedUrl("/me/profile"))
                .andExpect(flash().attribute("photoError", "JPG, PNG, GIF 사진만 올릴 수 있어요."));
        mvc.perform(multipart("/me/profile/photo").with(oauth2Login().oauth2User(login(me))).with(csrf()))
                .andExpect(flash().attribute("photoError", "올릴 사진을 골라 주세요."));
        var huge = new MockMultipartFile("photo", "huge.png", "image/png", new byte[(int) ProfileImageStorage.MAX_BYTES + 1]);
        mvc.perform(multipart("/me/profile/photo").file(huge).with(oauth2Login().oauth2User(login(me))).with(csrf()))
                .andExpect(flash().attribute("photoError", "사진은 5MB까지 올릴 수 있어요."));
        assertThat(userService.get(me.getId()).getProfileImageUrl()).isNull();
    }

    @Test
    void 우리가_올린_사진이_아닌_주소나_이상한_이름은_지우지_않는다() {
        // 경로 조작 시도는 무시한다 (예외 없이 아무것도 안 함)
        storage.deleteIfOwned(ProfileImageStorage.URL_PREFIX + "../../build.gradle");
        storage.deleteIfOwned("https://k.kakaocdn.net/photo.jpg");
        storage.deleteIfOwned(null);
        assertThat(Files.exists(java.nio.file.Path.of("build.gradle"))).isTrue();
    }

    @Test
    void 비밀번호를_바꾼다() throws Exception {
        User me = local("비번");
        String[][] bad = {
                {"wrong123", "newpass99", "newpass99", "지금 비밀번호가 맞지 않아요."},
                {"abcd1234", "newpass99", "newpass98", "새 비밀번호 두 칸이 서로 달라요."},
                {"abcd1234", "abcd1234", "abcd1234", "지금과 다른 비밀번호로"},
                {"abcd1234", "short1", "short1", "8~64자"},
                {"abcd1234", "onlyletters", "onlyletters", "영문과 숫자를 모두"},
        };
        for (String[] c : bad) {
            mvc.perform(post("/me/password").with(oauth2Login().oauth2User(login(me))).with(csrf())
                            .param("currentPassword", c[0]).param("newPassword", c[1]).param("newPasswordConfirm", c[2]))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(c[3])));
        }
        assertThat(passwordEncoder.matches("abcd1234", userService.get(me.getId()).getPassword())).isTrue();

        mvc.perform(post("/me/password").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("currentPassword", "abcd1234").param("newPassword", "newpass99").param("newPasswordConfirm", "newpass99"))
                .andExpect(redirectedUrl("/me/profile"));
        assertThat(passwordEncoder.matches("newpass99", userService.get(me.getId()).getPassword())).isTrue();
    }
}
