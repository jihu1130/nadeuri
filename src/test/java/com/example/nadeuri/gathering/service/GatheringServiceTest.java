package com.example.nadeuri.gathering.service;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.exception.AlreadyJoinedException;
import com.example.nadeuri.global.util.Times;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("h2")
class GatheringServiceTest {

    // 참여 기록은 users 테이블과 FK로 묶여 있지 않아 임의의 회원 ID로 테스트한다
    private static final long HOST_ID = 999L;

    @Autowired GatheringService service;

    private Gathering create(double lat, double lng, int max) {
        return service.create(new GatheringCreateRequest("테스트", null, ActivityTag.WALK, "장소",
                lat, lng, Times.now().plusDays(1), 120, max), HOST_ID);
    }

    @Test
    void 같은_사람은_두_번_참여할_수_없다() {
        Gathering g = create(37.5, 127.0, 5);
        service.join(g.getId(), 1001L);
        assertThatThrownBy(() -> service.join(g.getId(), 1001L))
                .isInstanceOf(AlreadyJoinedException.class);
        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(2);
    }

    @Test
    void 동시에_참여해도_정원을_넘지_않는다() throws Exception {
        Gathering g = create(37.5, 127.0, 5); // 주최자 1 + 4자리
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long userId = 2000L + i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.join(g.getId(), userId);
                    success.incrementAndGet();
                } catch (IllegalStateException ignored) {
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(success.get()).isEqualTo(4);
        assertThat(service.get(g.getId()).getCurrentPeople()).isEqualTo(5);
    }

    @Test
    void 날짜변경선_건너편_모임도_검색된다() {
        Gathering east = create(0, 179.99, 5);
        List<Long> ids = service.findNearby(0, -179.99, 5000).stream().map(Gathering::getId).toList();
        assertThat(ids).contains(east.getId());
    }
}
