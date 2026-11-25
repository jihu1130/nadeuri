package com.example.nadeuri.chat.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅 도배 방지: 한 회원이 1초 안에 MAX_PER_WINDOW개를 넘게 보내려 하면 BLOCK 동안 보낼 수 없다.
 * 회원 기준이라 같은 사람이 탭을 여러 개 열어도 함께 센다 (모임이 달라도 함께 센다).
 * 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다.
 */
@Component
public class ChatSendLimiter {

    public static final int MAX_PER_WINDOW = 5;
    public static final Duration WINDOW = Duration.ofSeconds(1);
    public static final Duration BLOCK = Duration.ofSeconds(3);

    private final Clock clock;
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public ChatSendLimiter() {
        this(Clock.systemUTC());
    }

    ChatSendLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 보내도 되면 0, 막혔으면 다시 보낼 수 있을 때까지 남은 밀리초(1 이상).
     * 보내도 된다고 답한 경우에만 한 번 보낸 것으로 센다.
     */
    public long tryAcquire(Long userId) {
        long now = clock.millis();
        State s = states.computeIfAbsent(userId, k -> new State());
        synchronized (s) {
            if (now < s.blockedUntil) {
                return s.blockedUntil - now;
            }
            while (!s.sent.isEmpty() && now - s.sent.peekFirst() >= WINDOW.toMillis()) {
                s.sent.pollFirst();
            }
            if (s.sent.size() >= MAX_PER_WINDOW) {
                s.blockedUntil = now + BLOCK.toMillis();
                s.sent.clear();
                return BLOCK.toMillis();
            }
            s.sent.addLast(now);
            return 0;
        }
    }

    private static final class State {
        final Deque<Long> sent = new ArrayDeque<>();
        long blockedUntil;
    }
}
