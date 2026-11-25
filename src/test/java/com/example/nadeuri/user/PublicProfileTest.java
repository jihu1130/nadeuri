package com.example.nadeuri.user;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.domain.Visibility;
import com.example.nadeuri.user.dto.ProfileUpdateRequest;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 회원 프로필(/users/{id}): 자기소개·평판은 누구나, 활동 기록은 공개 범위대로 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class PublicProfileTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipantRepository participantRepository;

    private User user(String nickname) {
        String username = "p" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static RequestPostProcessor as(User u) {
        return oauth2Login().oauth2User(new LoginUser(u.getId(), u.getNickname(), null));
    }

    /** 첫 사람이 주최자. 시작·끝은 지금 기준 분 */
    private Gathering gathering(String title, int startMin, int endMin, User host, User... members) {
        LocalDateTime now = Times.now();
        Gathering g = gatheringRepository.save(new Gathering(title, null, ActivityTag.WALK, "장소", 0, 0,
                now.plusMinutes(startMin), now.plusMinutes(endMin), 10));
        participantRepository.save(new Participant(g.getId(), host.getId(), true));
        for (User m : members) participantRepository.save(new Participant(g.getId(), m.getId(), false));
        return g;
    }

    private String url(User u) {
        return "/users/" + u.getPublicId();
    }

    @Test
    void 기본값_연_모임은_누구나_참여한_모임은_함께한_사람만() throws Exception {
        User owner = user("주인공"), friend = user("함께한친구"), stranger = user("처음보는"), host = user("남의모임장");
        userService.updateProfile(owner.getId(), new ProfileUpdateRequest("주인공", "한강 산책 좋아해요"));
        gathering("주인공이 연 모임", 60, 120, owner);
        gathering("같이 간 끝난 모임", -180, -60, host, owner, friend);

        // 로그인 안 한 사람: 자기소개·평판·연 모임은 보이고, 참여한 모임은 안 보인다
        mvc.perform(get(url(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("한강 산책 좋아해요")))
                .andExpect(content().string(containsString("받은 \"다음에 또 만나요\"")))
                .andExpect(content().string(containsString("주인공이 연 모임")))
                .andExpect(content().string(not(containsString("같이 간 끝난 모임"))))
                .andExpect(content().string(containsString("함께 모임에 참여한 사람에게만 보여요.")));
        // 함께한 적 없는 회원도 같다
        mvc.perform(get(url(owner)).with(as(stranger)))
                .andExpect(content().string(not(containsString("같이 간 끝난 모임"))));
        // 같은 모임에 있었던 사람에게는 보인다
        mvc.perform(get(url(owner)).with(as(friend)))
                .andExpect(content().string(containsString("같이 간 끝난 모임")));
    }

    @Test
    void 앞으로_참여할_모임은_누구에게도_보이지_않는다() throws Exception {
        User owner = user("주인공"), host = user("모임장");
        userService.changeVisibility(owner.getId(), Visibility.PUBLIC, Visibility.PUBLIC);
        gathering("다음 주 예정 모임", 60 * 24 * 7, 60 * 24 * 7 + 60, host, owner);
        gathering("지난 모임", -180, -60, host, owner);

        mvc.perform(get(url(owner)))
                .andExpect(content().string(containsString("지난 모임")))
                .andExpect(content().string(not(containsString("다음 주 예정 모임"))));
        mvc.perform(get(url(owner)).with(as(owner)))
                .andExpect(content().string(not(containsString("다음 주 예정 모임"))));
    }

    @Test
    void 나만_보기는_함께한_사람에게도_숨기고_나에게는_공개_범위와_함께_보인다() throws Exception {
        User owner = user("주인공"), friend = user("친구");
        userService.changeVisibility(owner.getId(), Visibility.PRIVATE, Visibility.PRIVATE);
        gathering("비밀 모임", -180, -60, owner, friend);

        mvc.perform(get(url(owner)).with(as(friend)))
                .andExpect(content().string(not(containsString("비밀 모임"))))
                .andExpect(content().string(containsString("공개하지 않은 기록이에요.")));
        mvc.perform(get(url(owner)).with(as(owner)))
                .andExpect(content().string(containsString("비밀 모임")))
                .andExpect(content().string(containsString("내 프로필이에요")))
                .andExpect(content().string(containsString("🔒 나만 보기")));
    }

    @Test
    void 없는_회원은_404() throws Exception {
        mvc.perform(get("/users/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void 프로필_설정에서_공개_범위를_바꾼다() throws Exception {
        User owner = user("주인공");
        mvc.perform(get("/me/profile").with(as(owner)))
                .andExpect(content().string(containsString("활동 기록 공개 범위")))
                .andExpect(content().string(containsString("id=\"joined-MEMBERS\" value=\"MEMBERS\" checked=\"checked\"")));

        mvc.perform(post("/me/profile/visibility").with(as(owner)).with(csrf())
                        .param("hosted", "MEMBERS").param("joined", "PRIVATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "활동 기록 공개 범위를 저장했어요."));
        User saved = userService.get(owner.getId());
        assertThat(saved.getHostedVisibility()).isEqualTo(Visibility.MEMBERS);
        assertThat(saved.getJoinedVisibility()).isEqualTo(Visibility.PRIVATE);

        // 이상한 값은 받지 않는다
        mvc.perform(post("/me/profile/visibility").with(as(owner)).with(csrf())
                        .param("hosted", "EVERYONE").param("joined", "PUBLIC"))
                .andExpect(status().isBadRequest());
        assertThat(userService.get(owner.getId()).getJoinedVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void 모임_상세의_참여자_이름은_프로필로_이어진다() throws Exception {
        User host = user("모임장");
        Gathering g = gathering("링크 확인", 60, 120, host);
        mvc.perform(get("/gatherings/" + g.getPublicId()))
                .andExpect(content().string(containsString("href=\"/users/" + host.getPublicId() + "\"")));
    }
}
