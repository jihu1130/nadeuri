package com.example.nadeuri.gathering.service;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.exception.GatheringLimitException;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nadeuri.gathering.service.GatheringService.MAX_ACTIVE_HOSTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 한 사람당 끝나지 않은 모임은 MAX_ACTIVE_HOSTED(5)개까지만 만들 수 있다 */
@SpringBootTest
@ActiveProfiles("h2")
class GatheringLimitTest {

    @Autowired GatheringService service;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired UserRepository userRepository;

    private Long newUser() {
        return userRepository.save(new User(User.PROVIDER_KAKAO, "limit" + System.nanoTime(), "제한테스트", null)).getId();
    }

    private Gathering create(Long hostId) {
        return service.create(new GatheringCreateRequest("제한 테스트", null, ActivityTag.OTHER, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 5), hostId);
    }

    @Test
    void 끝나지_않은_모임은_5개까지만_만들_수_있다() {
        Long me = newUser();
        for (int i = 0; i < MAX_ACTIVE_HOSTED; i++) create(me);
        assertThat(service.canCreate(me)).isFalse();
        assertThatThrownBy(() -> create(me)).isInstanceOf(GatheringLimitException.class);
        assertThat(service.hostedBy(me)).hasSize(MAX_ACTIVE_HOSTED);
        // 다른 사람은 영향 없음
        assertThat(service.canCreate(newUser())).isTrue();
    }

    @Test
    void 끝난_모임은_세지_않는다() {
        Long me = newUser();
        // 이미 끝난 모임 3개 (요청 검증을 거치지 않도록 저장소로 직접 넣음)
        for (int i = 0; i < 3; i++) {
            Gathering ended = gatheringRepository.save(new Gathering("끝난 모임", null, ActivityTag.WALK, "장소",
                    37.5, 127.0, Times.now().minusDays(2), Times.now().minusDays(2).plusHours(2), 5));
            participantRepository.save(new Participant(ended.getId(), me, true));
        }
        for (int i = 0; i < MAX_ACTIVE_HOSTED; i++) create(me);
        assertThatThrownBy(() -> create(me)).isInstanceOf(GatheringLimitException.class);
    }

    @Test
    void 모임을_삭제하면_다시_만들_수_있다() {
        Long me = newUser();
        Gathering first = create(me);
        for (int i = 1; i < MAX_ACTIVE_HOSTED; i++) create(me);
        service.delete(first.getId(), me);
        assertThat(service.canCreate(me)).isTrue();
        create(me);
    }

    @Test
    void 동시에_여러_번_만들어도_5개를_넘지_않는다() throws Exception {
        Long me = newUser();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    create(me);
                    success.incrementAndGet();
                } catch (GatheringLimitException ignored) {
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(success.get()).isEqualTo(MAX_ACTIVE_HOSTED);
        assertThat(service.hostedBy(me)).hasSize(MAX_ACTIVE_HOSTED);
    }
}
