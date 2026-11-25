package com.example.nadeuri.gathering;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.dto.ParticipantView;
import com.example.nadeuri.gathering.exception.NotHostException;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 주최자·참여자 목록, 참여 취소, 모임 삭제, 내 모임 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class GatheringMembershipTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired GatheringService service;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired UserService userService;

    private User user(String nickname) {
        String username = "m" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static LoginUser login(User u) {
        return new LoginUser(u.getId(), u.getNickname(), null);
    }

    private Gathering gathering(User host, String title) {
        return service.create(GatheringCreateRequest.of(title, null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 5), host.getId());
    }

    @Test
    void 참여자_목록은_주최자가_먼저_나오고_닉네임을_보여준다() {
        User host = user("주최자");
        User a = user("참여자A");
        Gathering g = gathering(host, "목록 테스트");
        service.join(g.getId(), a.getId());

        List<ParticipantView> list = service.participants(g.getId());
        assertThat(list).extracting(ParticipantView::nickname).containsExactly("주최자", "참여자A");
        assertThat(list.get(0).host()).isTrue();
        assertThat(service.isHost(g.getId(), host.getId())).isTrue();
        assertThat(service.isHost(g.getId(), a.getId())).isFalse();
    }

    @Test
    void 참여를_취소하면_목록에서_빠지고_인원이_줄어든다() {
        User host = user("주최");
        User a = user("취소할사람");
        Gathering g = gathering(host, "취소 테스트");
        service.join(g.getId(), a.getId());
        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(2);

        service.leave(g.getId(), a.getId());

        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(1);
        assertThat(service.hasJoined(g.getId(), a.getId())).isFalse();
        // 다시 참여할 수 있다
        service.join(g.getId(), a.getId());
        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(2);
    }

    @Test
    void 주최자는_참여_취소_대신_삭제해야_하고_참여하지_않은_사람은_취소할_수_없다() {
        User host = user("주최");
        User stranger = user("남");
        Gathering g = gathering(host, "취소 불가 테스트");

        assertThatThrownBy(() -> service.leave(g.getId(), host.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("주최자");
        assertThatThrownBy(() -> service.leave(g.getId(), stranger.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("참여하지 않은");
        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(1);
    }

    @Test
    void 주최자만_모임을_삭제할_수_있고_참여_기록도_함께_지워진다() {
        User host = user("주최");
        User a = user("참여");
        Gathering g = gathering(host, "삭제 테스트");
        service.join(g.getId(), a.getId());

        assertThatThrownBy(() -> service.delete(g.getId(), a.getId())).isInstanceOf(NotHostException.class);
        assertThat(gatheringRepository.existsById(g.getId())).isTrue();
        // 다른 참여자가 있으면 주최자도 지울 수 없다 (모임장을 넘기도록 안내)
        assertThatThrownBy(() -> service.delete(g.getId(), host.getId())).isInstanceOf(IllegalStateException.class);

        service.leave(g.getId(), a.getId());
        service.delete(g.getId(), host.getId());
        assertThat(gatheringRepository.existsById(g.getId())).isFalse();
        assertThat(service.participants(g.getId())).isEmpty();
    }

    @Test
    void 내가_만든_모임과_참여한_모임을_나눠서_보여준다() throws Exception {
        User me = user("나야나");
        User other = user("다른사람");
        Gathering mine = gathering(me, "내가만든모임");
        Gathering theirs = gathering(other, "남이만든모임");
        service.join(theirs.getId(), me.getId());

        assertThat(service.hostedBy(me.getId())).extracting(Gathering::getId).containsExactly(mine.getId());
        assertThat(service.joinedBy(me.getId())).extracting(Gathering::getId).containsExactly(theirs.getId());

        mvc.perform(get("/me/gatherings").with(oauth2Login().oauth2User(login(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내가만든모임")))
                .andExpect(content().string(containsString("남이만든모임")));
    }

    @Test
    void 내_모임은_로그인이_필요하다() throws Exception {
        mvc.perform(get("/me/gatherings")).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 상세_화면은_역할에_맞는_버튼을_보여준다() throws Exception {
        User host = user("호스트");
        User a = user("참여자");
        User b = user("구경꾼");
        Gathering g = gathering(host, "버튼 테스트");
        service.join(g.getId(), a.getId());
        String url = "/gatherings/" + g.getPublicId();

        // 주최자: 다른 참여자가 있으므로 삭제 대신 모임장 넘기기 (받을 사람 목록에는 공개 UUID)
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(host))))
                .andExpect(content().string(not(containsString(">모임 삭제</button>"))))
                .andExpect(content().string(containsString("모임장 넘기기")))
                .andExpect(content().string(containsString("value=\"" + a.getPublicId() + "\">참여자</option>")))
                .andExpect(content().string(containsString("모임장을 넘긴 뒤 참여 취소로 나갈 수 있어요")))
                .andExpect(content().string(not(containsString(">참여 취소</button>"))))
                .andExpect(content().string(containsString("호스트")))
                .andExpect(content().string(containsString("참여자 2명")));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(a))))
                .andExpect(content().string(containsString(">참여 취소</button>")))
                .andExpect(content().string(not(containsString(">모임 삭제</button>"))));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(b))))
                .andExpect(content().string(containsString(">참여하기</button>")))
                .andExpect(content().string(not(containsString(">참여 취소</button>"))))
                .andExpect(content().string(not(containsString(">모임 삭제</button>"))));
    }

    private static GatheringCreateRequest edited(String title, int maxPeople) {
        return GatheringCreateRequest.of(title, "설명 바꿈", ActivityTag.RUNNING, "새 장소",
                37.55, 126.9, Times.now().plusDays(2).withSecond(0).withNano(0), 120, maxPeople);
    }

    @Test
    void 주최자는_모임을_수정할_수_있다() {
        User host = user("주최");
        Gathering g = gathering(host, "고치기 전");

        service.update(g.getId(), host.getId(), edited("고친 뒤", 8));

        Gathering after = service.get(g.getId());
        assertThat(after.getTitle()).isEqualTo("고친 뒤");
        assertThat(after.getTag()).isEqualTo(ActivityTag.RUNNING);
        assertThat(after.getPlaceName()).isEqualTo("새 장소");
        assertThat(after.getMaxPeople()).isEqualTo(8);
        assertThat(after.getCurrentPeople()).isEqualTo(1);
    }

    @Test
    void 주최자가_아니면_수정할_수_없고_정원은_참여_인원보다_줄일_수_없다() {
        User host = user("주최");
        User a = user("참여1");
        User b = user("참여2");
        Gathering g = gathering(host, "정원 테스트");
        service.join(g.getId(), a.getId());
        service.join(g.getId(), b.getId());     // 지금 3명

        assertThatThrownBy(() -> service.update(g.getId(), a.getId(), edited("몰래 수정", 5)))
                .isInstanceOf(NotHostException.class);
        assertThatThrownBy(() -> service.update(g.getId(), host.getId(), edited("너무 줄임", 2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3명");
        assertThat(service.get(g.getId()).getTitle()).isEqualTo("정원 테스트");   // 아무것도 바뀌지 않음

        service.update(g.getId(), host.getId(), edited("딱 맞게", 3));          // 참여 인원과 같으면 가능
        assertThat(service.get(g.getId()).getMaxPeople()).isEqualTo(3);
        assertThat(service.get(g.getId()).isFull()).isTrue();
    }

    @Test
    void 수정_화면은_저장된_값을_채우고_주최자만_열_수_있다() throws Exception {
        User host = user("주최");
        User other = user("남");
        Gathering g = gathering(host, "채워질 제목");
        String url = "/gatherings/" + g.getPublicId() + "/edit";

        mvc.perform(get(url).with(oauth2Login().oauth2User(login(host))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"채워질 제목\"")))
                .andExpect(content().string(containsString("action=\"" + url + "\"")))
                .andExpect(content().string(containsString("수정 저장")));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(other))))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()))
                .andExpect(flash().attribute("message", "모임을 만든 사람만 할 수 있어요."));
        mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 수정_저장과_정원_오류_화면() throws Exception {
        User host = user("주최");
        User a = user("참여");
        Gathering g = gathering(host, "원래 제목");
        service.join(g.getId(), a.getId());     // 2명
        String url = "/gatherings/" + g.getPublicId() + "/edit";
        String start = Times.now().plusDays(3).withHour(15).withMinute(30).toLocalDate() + "T15:30";

        // 정원을 1명으로 줄이려 하면 검증(@Min 2)에서 막히고 입력한 제목은 남아 있다
        mvc.perform(post(url).with(oauth2Login().oauth2User(login(host))).with(csrf())
                        .param("title", "입력한 제목").param("tag", "WALK").param("placeName", "장소")
                        .param("latitude", "37.5").param("longitude", "127.0")
                        .param("startAt", start).param("durationMinutes", "90").param("maxPeople", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"입력한 제목\"")));

        mvc.perform(post(url).with(oauth2Login().oauth2User(login(host))).with(csrf())
                        .param("title", "바뀐 제목").param("tag", "PICNIC").param("placeName", "바뀐 장소")
                        .param("latitude", "37.5").param("longitude", "127.0")
                        .param("startAt", start).param("durationMinutes", "90").param("maxPeople", "10"))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()))
                .andExpect(flash().attribute("message", "모임 정보를 수정했어요."));
        Gathering after = service.get(g.getId());
        assertThat(after.getTitle()).isEqualTo("바뀐 제목");
        assertThat(after.getStartAt().getHour()).isEqualTo(15);
        assertThat(after.getStartAt().getMinute()).isEqualTo(30);
        assertThat(after.getEndAt()).isEqualTo(after.getStartAt().plusMinutes(90)); // 진행 시간도 함께 바뀜
    }

    @Test
    void 참여_취소와_삭제_요청이_화면에서_동작한다() throws Exception {
        User host = user("주최");
        User a = user("참여");
        Gathering g = gathering(host, "요청 테스트");
        service.join(g.getId(), a.getId());

        mvc.perform(post("/gatherings/" + g.getPublicId() + "/leave").with(oauth2Login().oauth2User(login(a))).with(csrf()))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()))
                .andExpect(flash().attribute("message", "참여를 취소했어요."));
        // 주최자가 아닌 사람의 삭제 요청은 거절
        mvc.perform(post("/gatherings/" + g.getPublicId() + "/delete").with(oauth2Login().oauth2User(login(a))).with(csrf()))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()));
        assertThat(gatheringRepository.existsById(g.getId())).isTrue();

        mvc.perform(post("/gatherings/" + g.getPublicId() + "/delete").with(oauth2Login().oauth2User(login(host))).with(csrf()))
                .andExpect(redirectedUrl("/me/gatherings"));
        assertThat(gatheringRepository.existsById(g.getId())).isFalse();
    }

    @Test
    void 모임장_넘기기와_참여자가_있을_때_삭제_요청이_화면에서_동작한다() throws Exception {
        User host = user("넘김주최");
        User a = user("받는사람");
        Gathering g = gathering(host, "넘기기 화면");
        service.join(g.getId(), a.getId());
        String url = "/gatherings/" + g.getPublicId();

        mvc.perform(post(url + "/delete").with(oauth2Login().oauth2User(login(host))).with(csrf()))
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attribute("message", containsString("모임장을 다른 참여자에게 넘겨")));
        assertThat(gatheringRepository.existsById(g.getId())).isTrue();

        mvc.perform(post(url + "/host").with(oauth2Login().oauth2User(login(host))).with(csrf()))
                .andExpect(flash().attribute("message", containsString("골라 주세요")));
        mvc.perform(post(url + "/host").param("to", a.getPublicId()).with(oauth2Login().oauth2User(login(host))).with(csrf()))
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attribute("message", containsString("모임장을 넘겼어요")));
        assertThat(service.isHost(g.getId(), a.getId())).isTrue();

        // 예전 주최자는 이제 참여 취소 버튼, 새 모임장은 넘기기 화면
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(host))))
                .andExpect(content().string(containsString(">참여 취소</button>")));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(a))))
                .andExpect(content().string(containsString("모임장 넘기기")));
    }

    @Test
    void 내_모임은_만든_모임_개수를_보여주고_다_차면_새_모임_대신_안내한다() throws Exception {
        User me = user("개수");
        for (int i = 1; i < GatheringService.MAX_ACTIVE_HOSTED; i++) gathering(me, "개수" + i);
        mvc.perform(get("/me/gatherings").with(oauth2Login().oauth2User(login(me))))
                .andExpect(content().string(containsString(">4/5</span>")))
                .andExpect(content().string(containsString("href=\"/gatherings/new\">+ 새 모임</a>")))
                .andExpect(content().string(not(containsString("id=\"newGatheringLimited\""))));

        gathering(me, "개수5");
        mvc.perform(get("/me/gatherings").with(oauth2Login().oauth2User(login(me))))
                .andExpect(content().string(containsString(">5/5</span>")))
                .andExpect(content().string(containsString("id=\"newGatheringLimited\"")))
                .andExpect(content().string(not(containsString("href=\"/gatherings/new\">+ 새 모임</a>"))))
                .andExpect(content().string(containsString("5개까지 열 수 있어요")));
    }
}
