package com.example.nadeuri.gathering.service;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.exception.NotHostException;
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

/** 모임장 넘기기, 다른 참여자가 있을 때 삭제 막기 */
@SpringBootTest
@ActiveProfiles("h2")
class HostTransferTest {

    @Autowired GatheringService service;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired UserRepository userRepository;

    private User user(String nickname) {
        return userRepository.save(new User(User.PROVIDER_KAKAO, "ht" + System.nanoTime(), nickname, null));
    }

    private Gathering create(User host) {
        return service.create(new GatheringCreateRequest("넘기기 테스트", null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 6), host.getId());
    }

    @Test
    void 혼자인_모임은_삭제할_수_있다() {
        User host = user("혼자");
        Gathering g = create(host);
        assertThat(service.mustTransferBeforeDelete(g)).isFalse();
        service.delete(g.getId(), host.getId());
        assertThat(gatheringRepository.findById(g.getId())).isEmpty();
    }

    @Test
    void 다른_참여자가_있으면_삭제하지_않고_모임장을_넘기라고_한다() {
        User host = user("주최"), member = user("참여");
        Gathering g = create(host);
        service.join(g.getId(), member.getId());

        assertThat(service.mustTransferBeforeDelete(g)).isTrue();
        assertThatThrownBy(() -> service.delete(g.getId(), host.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("모임장을 다른 참여자에게 넘겨");
        assertThat(gatheringRepository.findById(g.getId())).isPresent();
        assertThat(service.hasJoined(g.getId(), member.getId())).isTrue();
    }

    @Test
    void 끝난_모임은_다른_참여자가_있어도_삭제할_수_있다() {
        User host = user("끝주최"), member = user("끝참여");
        Gathering ended = gatheringRepository.save(new Gathering("끝난 모임", null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().minusDays(1), Times.now().minusDays(1).plusHours(2), 5));
        participantRepository.save(new Participant(ended.getId(), host.getId(), true));
        participantRepository.save(new Participant(ended.getId(), member.getId(), false));

        service.delete(ended.getId(), host.getId());
        assertThat(gatheringRepository.findById(ended.getId())).isEmpty();
    }

    @Test
    void 모임장을_넘기면_받은_사람이_모임장이_되고_넘긴_사람은_참여자로_남는다() {
        User host = user("넘기는"), member = user("받는");
        Gathering g = create(host);
        service.join(g.getId(), member.getId());

        service.transferHost(g.getId(), host.getId(), member.getPublicId());

        assertThat(service.isHost(g.getId(), member.getId())).isTrue();
        assertThat(service.isHost(g.getId(), host.getId())).isFalse();
        assertThat(service.hasJoined(g.getId(), host.getId())).isTrue();
        assertThat(service.participants(g.getId())).filteredOn(p -> p.host()).hasSize(1);
        assertThat(service.joinedBy(host.getId())).extracting(Gathering::getId).contains(g.getId());
        assertThat(service.hostedBy(member.getId())).extracting(Gathering::getId).contains(g.getId());
        // 이제 예전 주최자는 수정·삭제·다시 넘기기를 할 수 없다
        assertThatThrownBy(() -> service.transferHost(g.getId(), host.getId(), member.getPublicId()))
                .isInstanceOf(NotHostException.class);
        // 넘긴 사람은 참여 취소로 나갈 수 있고, 새 모임장은 혼자가 되면 삭제할 수 있다
        service.leave(g.getId(), host.getId());
        service.delete(g.getId(), member.getId());
    }

    @Test
    void 참여자가_아니거나_없는_사람에게는_넘길_수_없다() {
        User host = user("주최2"), outsider = user("밖");
        Gathering g = create(host);
        assertThatThrownBy(() -> service.transferHost(g.getId(), host.getId(), outsider.getPublicId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("참여한 사람에게만");
        assertThatThrownBy(() -> service.transferHost(g.getId(), host.getId(), "없는-사람"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("골라 주세요");
        assertThatThrownBy(() -> service.transferHost(g.getId(), host.getId(), host.getPublicId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미");
        // 실패해도 모임장은 그대로
        assertThat(service.isHost(g.getId(), host.getId())).isTrue();
    }

    @Test
    void 받는_사람이_이미_모임을_5개_열었으면_넘길_수_없다() {
        User host = user("주최3"), busy = user("바쁜");
        for (int i = 0; i < MAX_ACTIVE_HOSTED; i++) create(busy);
        Gathering g = create(host);
        service.join(g.getId(), busy.getId());

        assertThatThrownBy(() -> service.transferHost(g.getId(), host.getId(), busy.getPublicId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("바쁜님은 이미 모임을 5개");
        assertThat(service.isHost(g.getId(), host.getId())).isTrue();
    }

    @Test
    void 동시에_두_사람에게_넘겨도_모임장은_한_명이다() throws Exception {
        for (int round = 0; round < 3; round++) {
            User host = user("동시주최"), a = user("가"), b = user("나");
            Gathering g = create(host);
            service.join(g.getId(), a.getId());
            service.join(g.getId(), b.getId());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (User to : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.transferHost(g.getId(), host.getId(), to.getPublicId());
                        success.incrementAndGet();
                    } catch (NotHostException | IllegalStateException ignored) {
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(success.get()).isEqualTo(1);
            assertThat(service.participants(g.getId())).filteredOn(p -> p.host()).hasSize(1);
        }
    }
}
