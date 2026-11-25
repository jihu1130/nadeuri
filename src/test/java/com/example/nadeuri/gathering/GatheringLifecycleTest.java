package com.example.nadeuri.gathering;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.GatheringStatus;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.dto.GatheringFormView;
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

import java.time.LocalDateTime;
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

/** 모임 수명주기: 끝나는 시간, 모집 중 → 진행 중 → 종료, 검색·참여 규칙 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class GatheringLifecycleTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    /** 다른 테스트의 모임과 섞이지 않도록 아무도 쓰지 않는 좌표에서 검색한다 */
    private static final double LAT = -33.0, LNG = 151.0;

    @Autowired MockMvc mvc;
    @Autowired GatheringService service;
    @Autowired GatheringRepository repository;
    @Autowired UserService userService;

    private User user(String nickname) {
        String username = "l" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static LoginUser login(User u) {
        return new LoginUser(u.getId(), u.getNickname(), null);
    }

    /** 시작·끝을 지금 기준 분 단위로 정해 바로 저장 (지난 시간도 가능) */
    private Gathering saved(String title, int startFromNowMin, int endFromNowMin, int max) {
        LocalDateTime now = Times.now();
        return repository.save(new Gathering(title, null, ActivityTag.WALK, "장소", LAT, LNG,
                now.plusMinutes(startFromNowMin), now.plusMinutes(endFromNowMin), max));
    }

    private Gathering hosted(User host, String title, int startFromNowMin, int durationMin) {
        return service.create(new GatheringCreateRequest(title, null, ActivityTag.WALK, "장소", LAT, LNG,
                Times.now().plusMinutes(startFromNowMin), durationMin, 5), host.getId());
    }

    @Test
    void 상태는_시간과_인원으로_계산된다() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 23, 14, 0);
        Gathering g = new Gathering("상태", null, ActivityTag.WALK, "장소", 0, 0, t, t.plusHours(2), 2);

        assertThat(g.statusAt(t.minusMinutes(1))).isEqualTo(GatheringStatus.RECRUITING);
        assertThat(g.statusAt(t)).isEqualTo(GatheringStatus.ONGOING);           // 시작 시각부터 진행 중
        assertThat(g.statusAt(t.plusHours(2).minusNanos(1))).isEqualTo(GatheringStatus.ONGOING);
        assertThat(g.statusAt(t.plusHours(2))).isEqualTo(GatheringStatus.ENDED); // 끝나는 시각부터 종료
        assertThat(g.durationMinutes()).isEqualTo(120);
    }

    @Test
    void 정원이_찬_시작_전_모임은_모집_마감() {
        Gathering g = saved("마감", 60, 120, 2);
        service.join(g.getId(), user("채움").getId());   // 1/2 → 2/2
        assertThat(service.get(g.getId()).statusAt(Times.now())).isEqualTo(GatheringStatus.FULL);
    }

    @Test
    void 끝나는_시간은_시작보다_뒤여야_한다() {
        LocalDateTime t = Times.now();
        assertThatThrownBy(() -> new Gathering("x", null, ActivityTag.WALK, "p", 0, 0, t, t, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주변_검색에_진행_중인_모임은_나오고_끝난_모임은_빠진다() {
        Gathering upcoming = saved("검색-예정", 60, 180, 5);
        Gathering ongoing = saved("검색-진행", -30, 90, 5);
        Gathering ended = saved("검색-종료", -180, -60, 5);

        assertThat(service.findNearby(LAT, LNG, 500))
                .extracting(Gathering::getId)
                .contains(ongoing.getId(), upcoming.getId())
                .doesNotContain(ended.getId());
    }

    @Test
    void 진행_중인_모임은_늦게_합류할_수_있지만_끝난_모임은_참여할_수_없다() {
        User a = user("늦게온사람");
        Gathering ongoing = saved("합류", -30, 90, 5);
        Gathering ended = saved("끝남", -180, -60, 5);

        service.join(ongoing.getId(), a.getId());
        assertThat(service.hasJoined(ongoing.getId(), a.getId())).isTrue();
        assertThat(service.get(ongoing.getId()).getCurrentPeople()).isEqualTo(2);

        assertThatThrownBy(() -> service.join(ended.getId(), a.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("이미 끝난 모임이에요.");
        // 시작한 뒤에는 참여 취소는 여전히 막는다
        assertThatThrownBy(() -> service.leave(ongoing.getId(), a.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 만들기_요청의_진행_시간으로_끝나는_시간을_정한다() throws Exception {
        User host = user("만드는사람");
        String start = Times.now().plusDays(2).toLocalDate() + "T10:00";

        mvc.perform(post("/gatherings").with(oauth2Login().oauth2User(login(host))).with(csrf())
                        .param("title", "진행 시간 테스트").param("tag", "WALK").param("placeName", "장소")
                        .param("latitude", "37.5").param("longitude", "127.0")
                        .param("startAt", start).param("durationMinutes", "150").param("maxPeople", "6"))
                .andExpect(status().is3xxRedirection());

        Gathering g = service.hostedBy(host.getId()).get(0);
        assertThat(g.getEndAt()).isEqualTo(g.getStartAt().plusMinutes(150));
        assertThat(g.getEndAt().getHour()).isEqualTo(12);
        assertThat(g.getEndAt().getMinute()).isEqualTo(30);

        // 수정 화면: 목록에 없는 150분도 선택된 채로 나온다
        mvc.perform(get("/gatherings/" + g.getPublicId() + "/edit").with(oauth2Login().oauth2User(login(host))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<option value=\"150\" selected=\"selected\">2시간 30분</option>")));
    }

    @Test
    void 진행_시간이_없거나_범위를_벗어나면_다시_입력받는다() throws Exception {
        User host = user("검증");
        String start = Times.now().plusDays(2).toLocalDate() + "T10:00";
        String[][] cases = {{null, "진행 시간을 골라 주세요."}, {"10", "30분 이상"}, {"2000", "24시간까지"}};
        for (String[] c : cases) {
            var req = post("/gatherings").with(oauth2Login().oauth2User(login(host))).with(csrf())
                    .param("title", "검증").param("tag", "WALK").param("placeName", "장소")
                    .param("latitude", "37.5").param("longitude", "127.0")
                    .param("startAt", start).param("maxPeople", "6");
            if (c[0] != null) req.param("durationMinutes", c[0]);
            mvc.perform(req).andExpect(status().isOk())
                    .andExpect(content().string(containsString(c[1])));
        }
        assertThat(service.hostedBy(host.getId())).isEmpty();
    }

    @Test
    void 장소를_고르지_않거나_이상한_값이면_장소를_선택하라는_안내를_한_번만_보여준다() throws Exception {
        User host = user("장소없음");
        String start = Times.now().plusDays(2).toLocalDate() + "T10:00";
        // 위도·경도 없음 / 빈 값 / 숫자가 아님 / 범위 밖
        String[][] cases = {{null, null}, {"", ""}, {"abc", "xyz"}, {"999", "127.0"}};
        for (String[] c : cases) {
            var req = post("/gatherings").with(oauth2Login().oauth2User(login(host))).with(csrf())
                    .param("title", "장소 없음").param("tag", "WALK").param("placeName", "장소")
                    .param("startAt", start).param("durationMinutes", "120").param("maxPeople", "6");
            if (c[0] != null) req.param("latitude", c[0]).param("longitude", c[1]);
            String html = mvc.perform(req).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("널이어서는"))))
                    .andExpect(content().string(not(containsString("Failed to convert"))))
                    .andReturn().getResponse().getContentAsString();
            assertThat(html.split("모임 장소를 선택해 주세요", -1)).as("안내는 한 번만: %s", (Object) c).hasSize(2);
        }
        assertThat(service.hostedBy(host.getId())).isEmpty();
    }

    @Test
    void 장소_이름이_너무_길면_오류_화면_대신_다시_입력받는다() throws Exception {
        User host = user("긴장소");
        String start = Times.now().plusDays(2).toLocalDate() + "T10:00";
        mvc.perform(post("/gatherings").with(oauth2Login().oauth2User(login(host))).with(csrf())
                        .param("title", "긴 장소").param("tag", "WALK").param("placeName", "가".repeat(256))
                        .param("latitude", "37.5").param("longitude", "127.0")
                        .param("startAt", start).param("durationMinutes", "120").param("maxPeople", "6"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("장소 이름은 255자까지")));
        assertThat(service.hostedBy(host.getId())).isEmpty();
    }

    @Test
    void 상세_화면은_상태_배지와_참여_가능_여부를_보여준다() throws Exception {
        User a = user("구경");
        Gathering ongoing = saved("상세-진행", -30, 90, 5);
        Gathering ended = saved("상세-종료", -180, -60, 5);

        mvc.perform(get("/gatherings/" + ongoing.getPublicId()).with(oauth2Login().oauth2User(login(a))))
                .andExpect(content().string(containsString("status-badge ongoing")))
                .andExpect(content().string(containsString("늦게라도 합류할 수 있어요")))
                .andExpect(content().string(containsString(">참여하기<")));

        mvc.perform(get("/gatherings/" + ended.getPublicId()).with(oauth2Login().oauth2User(login(a))))
                .andExpect(content().string(containsString("status-badge ended")))
                .andExpect(content().string(containsString("끝난 모임")))
                .andExpect(content().string(not(containsString(">참여하기<"))));
    }

    @Test
    void 내_모임은_진행_중인_모임을_위에_두고_끝난_모임만_접어둔다() throws Exception {
        User host = user("내모임");
        hosted(host, "내모임-진행", -30, 120);
        hosted(host, "내모임-종료", -300, 60);

        String html = mvc.perform(get("/me/gatherings").with(oauth2Login().oauth2User(login(host))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("끝난 모임 1개");
        int pastSection = html.indexOf("끝난 모임 1개");
        assertThat(html.indexOf("내모임-진행")).isBetween(0, pastSection);
        assertThat(html.indexOf("내모임-종료")).isGreaterThan(pastSection);
        assertThat(html).contains("status-badge ongoing").contains("status-badge ended");
    }

    @Test
    void 진행_시간_선택지에_목록_밖의_저장값도_들어간다() {
        GatheringFormView f = new GatheringFormView(null, null, null, null, null, null, null, 150, 6);
        assertThat(f.durationOptions()).extracting(GatheringFormView.DurationOption::label)
                .contains("30분", "1시간 30분", "2시간 30분", "12시간");
        assertThat(GatheringFormView.empty().durationMinutes()).isEqualTo(120);
    }
}
