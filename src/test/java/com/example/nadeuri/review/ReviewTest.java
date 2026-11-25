package com.example.nadeuri.review;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.review.dto.Reputation;
import com.example.nadeuri.review.dto.ReviewRequest;
import com.example.nadeuri.review.repository.GatheringReviewRepository;
import com.example.nadeuri.review.repository.MeetAgainRepository;
import com.example.nadeuri.review.service.ReviewService;
import com.example.nadeuri.user.domain.User;
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
import java.util.List;
import java.util.concurrent.*;
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

/** 끝난 모임 후기·"다음에 또 만나요"·평판 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ReviewTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ReviewService reviewService;
    @Autowired GatheringService gatheringService;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired GatheringReviewRepository reviewRepository;
    @Autowired MeetAgainRepository meetAgainRepository;
    @Autowired UserService userService;

    private User user(String nickname) {
        String username = "r" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return userService.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static RequestPostProcessor as(User u) {
        return oauth2Login().oauth2User(new LoginUser(u.getId(), u.getNickname(), null));
    }

    /** 지금 기준 분 단위로 시작·끝을 정한 모임. 첫 사람이 주최자, 나머지는 참여자 */
    private Gathering gathering(int startFromNowMin, int endFromNowMin, User host, User... members) {
        LocalDateTime now = Times.now();
        Gathering g = gatheringRepository.save(new Gathering("후기 모임", null, ActivityTag.WALK, "장소", 0, 0,
                now.plusMinutes(startFromNowMin), now.plusMinutes(endFromNowMin), 10));
        participantRepository.save(new Participant(g.getId(), host.getId(), true));
        for (User m : members) participantRepository.save(new Participant(g.getId(), m.getId(), false));
        return g;
    }

    private Gathering ended(User host, User... members) {
        return gathering(-180, -60, host, members);
    }

    @Test
    void 끝나기_전이나_참여하지_않은_사람은_후기를_남길_수_없다() {
        User host = user("주최"), member = user("참여"), stranger = user("구경");
        Gathering upcoming = gathering(60, 120, host, member);
        assertThatThrownBy(() -> reviewService.write(upcoming, member.getId(), new ReviewRequest(5, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("끝난 뒤에");

        Gathering done = ended(host, member);
        assertThatThrownBy(() -> reviewService.write(done, stranger.getId(), new ReviewRequest(5, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("참여한 사람만");
    }

    @Test
    void 후기는_한_사람에_하나이고_다시_쓰면_고쳐진다() throws Exception {
        User host = user("주최"), member = user("참여");
        Gathering g = ended(host, member);

        mvc.perform(post("/gatherings/" + g.getPublicId() + "/review").with(as(member)).with(csrf())
                        .param("rating", "4").param("content", "  <b>즐거웠어요</b>  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId() + "#reviews"))
                .andExpect(flash().attribute("reviewMessage", "후기를 남겼어요. 고마워요!"));
        mvc.perform(post("/gatherings/" + g.getPublicId() + "/review").with(as(member)).with(csrf())
                        .param("rating", "2").param("content", "다시 생각해 보니 그냥 그랬어요"))
                .andExpect(flash().attribute("reviewMessage", "후기를 고쳤어요."));

        var saved = reviewRepository.findByGatheringIdAndUserId(g.getId(), member.getId()).orElseThrow();
        assertThat(saved.getRating()).isEqualTo(2);
        assertThat(reviewRepository.findViews(g.getId())).hasSize(1);

        // 상세: 평균, 후기 글, 고치기 화면
        mvc.perform(get("/gatherings/" + g.getPublicId()).with(as(member)))
                .andExpect(content().string(containsString("모임 후기")))
                .andExpect(content().string(containsString("다시 생각해 보니 그냥 그랬어요")))
                .andExpect(content().string(containsString("내 후기 고치기")))
                .andExpect(content().string(containsString("id=\"star2\" value=\"2\" required checked=\"checked\"")));
    }

    @Test
    void 후기_글은_태그로_해석하지_않고_그대로_보여준다() throws Exception {
        User host = user("주최"), member = user("참여");
        Gathering g = ended(host, member);
        reviewService.write(g, member.getId(), new ReviewRequest(5, "<script>alert(1)</script>"));
        mvc.perform(get("/gatherings/" + g.getPublicId()))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void 별점이_없거나_범위를_벗어나면_저장하지_않는다() throws Exception {
        User host = user("주최"), member = user("참여");
        Gathering g = ended(host, member);
        String[][] cases = {{null, "별점을 골라 주세요."}, {"0", "1~5개"}, {"6", "1~5개"}, {"abc", "별점을 골라 주세요."}};
        for (String[] c : cases) {
            var req = post("/gatherings/" + g.getPublicId() + "/review").with(as(member)).with(csrf()).param("content", "x");
            if (c[0] != null) req.param("rating", c[0]);
            mvc.perform(req).andExpect(flash().attribute("reviewMessage", containsString(c[1])));
        }
        mvc.perform(post("/gatherings/" + g.getPublicId() + "/review").with(as(member)).with(csrf())
                        .param("rating", "5").param("content", "가".repeat(201)))
                .andExpect(flash().attribute("reviewMessage", "후기는 200자까지 쓸 수 있어요."));
        assertThat(reviewRepository.findViews(g.getId())).isEmpty();
    }

    @Test
    void 끝나고_14일이_지나면_새로_남길_수_없지만_남긴_후기는_보인다() throws Exception {
        User host = user("주최"), member = user("참여"), late = user("늦음");
        int days15 = 15 * 24 * 60;
        Gathering g = gathering(-days15 - 60, -days15, host, member, late);
        // 기간 안에 남긴 것처럼 바로 저장해 둔다
        reviewRepository.save(new com.example.nadeuri.review.domain.GatheringReview(g.getId(), member.getId(), 5, "좋았어요"));

        assertThatThrownBy(() -> reviewService.write(g, late.getId(), new ReviewRequest(4, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("14일 안에만");
        mvc.perform(get("/gatherings/" + g.getPublicId()).with(as(late)))
                .andExpect(content().string(containsString("좋았어요")))
                .andExpect(content().string(containsString("기간(모임이 끝나고 14일)이 지났어요")))
                .andExpect(content().string(not(containsString("오늘 모임 어땠어요?"))));
    }

    @Test
    void 또_만나요는_함께한_사람에게만_보내고_다시_누르면_거둔다() throws Exception {
        User host = user("주최"), a = user("에이"), stranger = user("남");
        Gathering g = ended(host, a);
        String url = "/gatherings/" + g.getPublicId() + "/meet-again";

        mvc.perform(post(url).with(as(a)).with(csrf()).param("to", host.getPublicId()))
                .andExpect(flash().attribute("reviewMessage", containsString("보냈어요")));
        assertThat(reviewService.reputation(host.getId()).meetAgain()).isEqualTo(1);
        mvc.perform(get("/gatherings/" + g.getPublicId()).with(as(a)))
                .andExpect(content().string(containsString("👋 보냈어요")))
                .andExpect(content().string(containsString("👋 또 만나요 1")));

        mvc.perform(post(url).with(as(a)).with(csrf()).param("to", host.getPublicId()))
                .andExpect(flash().attribute("reviewMessage", containsString("거뒀어요")));
        assertThat(reviewService.reputation(host.getId()).meetAgain()).isZero();

        // 나 자신, 함께하지 않은 사람, 없는 사람에게는 안 됨
        for (String to : new String[]{a.getPublicId(), stranger.getPublicId(), "nope", ""}) {
            mvc.perform(post(url).with(as(a)).with(csrf()).param("to", to))
                    .andExpect(flash().attribute("reviewMessage", "이 모임에서 함께한 사람에게만 보낼 수 있어요."));
        }
        // 참여하지 않은 사람은 보낼 수 없다
        assertThatThrownBy(() -> reviewService.toggleMeetAgain(g, stranger.getId(), host.getPublicId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(meetAgainRepository.sentTo(g.getId(), a.getId())).isEmpty();
    }

    @Test
    void 모임장_별점은_다른_참여자가_준_별점의_평균이다() {
        User host = user("주최"), a = user("에이"), b = user("비");
        Gathering g1 = ended(host, a, b);
        Gathering g2 = ended(host, a);
        reviewService.write(g1, host.getId(), new ReviewRequest(5, "내 모임 최고"));   // 본인 별점은 빠진다
        reviewService.write(g1, a.getId(), new ReviewRequest(4, null));
        reviewService.write(g1, b.getId(), new ReviewRequest(3, null));
        reviewService.write(g2, a.getId(), new ReviewRequest(5, null));

        Reputation r = reviewService.reputation(host.getId());
        assertThat(r.hostReviews()).isEqualTo(3);
        assertThat(r.hostRatingLabel()).isEqualTo("4.0");
        // 참여자 a는 모임장이 아니므로 모임장 별점이 없다
        assertThat(reviewService.reputation(a.getId()).hostReviews()).isZero();
    }

    @Test
    void 모임을_지우거나_탈퇴하면_후기와_또_만나요도_지운다() {
        User host = user("주최"), a = user("에이"), b = user("비");
        Gathering g = ended(host, a, b);
        reviewService.write(g, a.getId(), new ReviewRequest(5, null));
        reviewService.write(g, b.getId(), new ReviewRequest(4, null));
        reviewService.toggleMeetAgain(g, a.getId(), b.getPublicId());
        reviewService.toggleMeetAgain(g, b.getId(), a.getPublicId());
        reviewService.toggleMeetAgain(g, b.getId(), host.getPublicId());

        // a 탈퇴: a가 쓴 후기, a가 보내고 받은 "또 만나요"가 없어진다
        userService.withdraw(a.getId(), "abcd1234");
        assertThat(reviewRepository.findByGatheringIdAndUserId(g.getId(), a.getId())).isEmpty();
        assertThat(reviewService.reputation(b.getId()).meetAgain()).isZero();
        assertThat(reviewService.reputation(host.getId()).meetAgain()).isEqualTo(1);

        // 끝난 모임 삭제: 남은 후기와 "또 만나요"도 함께
        gatheringService.delete(g.getId(), host.getId());
        assertThat(reviewRepository.findViews(g.getId())).isEmpty();
        assertThat(meetAgainRepository.sentTo(g.getId(), b.getId())).isEmpty();
    }

    @Test
    void 내_모임_화면은_후기를_안_쓴_끝난_모임에_후기_남기기를_보여준다() throws Exception {
        User host = user("주최"), a = user("에이");
        Gathering g = ended(host, a);
        mvc.perform(get("/me/gatherings").with(as(a)))
                .andExpect(content().string(containsString("✍ 후기 남기기")))
                .andExpect(content().string(containsString("/gatherings/" + g.getPublicId() + "#reviews")));
        reviewService.write(g, a.getId(), new ReviewRequest(5, null));
        mvc.perform(get("/me/gatherings").with(as(a)))
                .andExpect(content().string(not(containsString("✍ 후기 남기기"))));

        // 프로필의 내 평판
        reviewService.toggleMeetAgain(g, host.getId(), a.getPublicId());
        mvc.perform(get("/me/profile").with(as(a)))
                .andExpect(content().string(containsString("내 평판")))
                .andExpect(content().string(containsString("👋 1")));
    }

    @Test
    void 같은_사람이_동시에_여러_번_보내도_후기는_하나만_남는다() throws Exception {
        User host = user("주최"), a = user("에이");
        Gathering g = ended(host, a);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int rating = i % 5 + 1;
            futures.add(pool.submit(() -> {
                start.await();
                reviewService.write(g, a.getId(), new ReviewRequest(rating, "동시에"));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(reviewRepository.findViews(g.getId())).hasSize(1);
    }
}
