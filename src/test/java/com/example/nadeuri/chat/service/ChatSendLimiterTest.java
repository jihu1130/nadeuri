package com.example.nadeuri.chat.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 1초에 5개까지는 보내고, 6번째부터 3초 동안 막는다 (시계를 직접 움직여 확인) */
class ChatSendLimiterTest {

    /** 테스트에서 시간을 마음대로 움직이는 시계 */
    static class TestClock extends Clock {
        long now = 1_000_000L;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    @Test
    void 일초에_다섯_개까지는_보낼_수_있고_여섯_번째부터_삼초_막힌다() {
        TestClock clock = new TestClock();
        ChatSendLimiter limiter = new ChatSendLimiter(clock);

        for (int i = 0; i < ChatSendLimiter.MAX_PER_WINDOW; i++) {
            assertThat(limiter.tryAcquire(1L)).as("%d번째", i + 1).isZero();
            clock.now += 100;
        }
        assertThat(limiter.tryAcquire(1L)).as("6번째").isEqualTo(3000);

        clock.now += 1000;
        assertThat(limiter.tryAcquire(1L)).as("막힌 동안은 남은 시간").isEqualTo(2000);

        clock.now += 2000;
        assertThat(limiter.tryAcquire(1L)).as("3초 지나면 다시 보냄").isZero();
    }

    @Test
    void 천천히_보내면_막히지_않는다() {
        TestClock clock = new TestClock();
        ChatSendLimiter limiter = new ChatSendLimiter(clock);
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire(1L)).isZero();
            clock.now += 250;   // 1초에 4개
        }
    }

    @Test
    void 사람마다_따로_센다() {
        TestClock clock = new TestClock();
        ChatSendLimiter limiter = new ChatSendLimiter(clock);
        for (int i = 0; i < 6; i++) limiter.tryAcquire(1L);
        assertThat(limiter.tryAcquire(1L)).isPositive();
        assertThat(limiter.tryAcquire(2L)).isZero();
    }
}
