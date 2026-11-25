package com.example.nadeuri.user.controller;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.repository.UserRepository;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 회원 탈퇴 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class WithdrawTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired GatheringService gatheringService;
    @Autowired GatheringRepository gatheringRepository;

    private User localUser(String nickname) {
        String username = "w" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static LoginUser login(User u) {
        return new LoginUser(u.getId(), u.getNickname(), null);
    }

    private Gathering hosted(User host) {
        return gatheringService.create(GatheringCreateRequest.of("탈퇴 테스트", null, ActivityTag.WALK, "장소",
                -40.0, 175.0, Times.now().plusDays(1), 120, 5), host.getId());
    }

    @Test
    void 탈퇴하면_만든_모임은_지워지고_참여한_모임에서는_빠진다() throws Exception {
        User me = localUser("떠나는사람");
        User other = localUser("남는사람");
        Gathering mine = hosted(me);
        gatheringService.join(mine.getId(), other.getId());
        Gathering theirs = hosted(other);
        gatheringService.join(theirs.getId(), me.getId());
        assertThat(gatheringService.get(theirs.getId()).getCurrentPeople()).isEqualTo(2);

        mvc.perform(get("/me/withdraw").with(oauth2Login().oauth2User(login(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀번호 확인")));

        mvc.perform(post("/me/withdraw").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("password", "abcd1234").param("confirm", "true"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", containsString("탈퇴가 완료")));

        assertThat(userRepository.findById(me.getId())).isEmpty();
        assertThat(gatheringRepository.findById(mine.getId())).isEmpty();
        assertThat(gatheringService.get(theirs.getId()).getCurrentPeople()).isEqualTo(1);
        assertThat(gatheringService.hasJoined(theirs.getId(), me.getId())).isFalse();
        assertThat(gatheringService.joinedBy(other.getId())).isEmpty(); // 지워진 모임에서도 빠짐
        assertThat(userRepository.findById(other.getId())).isPresent();
    }

    @Test
    void 비밀번호가_틀리거나_동의하지_않으면_탈퇴되지_않는다() throws Exception {
        User me = localUser("망설이는사람");
        Gathering mine = hosted(me);

        mvc.perform(post("/me/withdraw").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("password", "wrong1234").param("confirm", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀번호가 맞지 않아요")));
        mvc.perform(post("/me/withdraw").with(oauth2Login().oauth2User(login(me))).with(csrf())
                        .param("password", "abcd1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("체크해 주세요")));

        assertThat(userRepository.findById(me.getId())).isPresent();
        assertThat(gatheringRepository.findById(mine.getId())).isPresent();
    }

    @Test
    void 카카오_회원은_비밀번호_없이_동의만으로_탈퇴한다() throws Exception {
        User kakao = userRepository.save(new User(User.PROVIDER_KAKAO, "k" + System.nanoTime(), "카카오회원", null));

        mvc.perform(get("/me/withdraw").with(oauth2Login().oauth2User(login(kakao))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("비밀번호 확인"))));
        mvc.perform(post("/me/withdraw").with(oauth2Login().oauth2User(login(kakao))).with(csrf())
                        .param("confirm", "true"))
                .andExpect(redirectedUrl("/login"));
        assertThat(userRepository.findById(kakao.getId())).isEmpty();
    }

    @Test
    void 로그인하지_않으면_탈퇴_화면에_들어갈_수_없다() throws Exception {
        mvc.perform(get("/me/withdraw")).andExpect(redirectedUrlPattern("**/login"));
    }
}
