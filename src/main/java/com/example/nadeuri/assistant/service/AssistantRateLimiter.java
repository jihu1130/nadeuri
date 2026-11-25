package com.example.nadeuri.assistant.service;

import com.example.nadeuri.assistant.exception.AssistantRateLimitedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 회원 한 명이 AI 도우미에게 물어볼 수 있는 횟수 제한 (Gemini 무료 사용량을 한 사람이 다 쓰지 않게).
 * 1분에 PER_MINUTE번, 하루(마지막 질문부터 24시간이 아니라 첫 질문부터 24시간)에 PER_DAY번.
 * 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다.
 */
@Service
public class AssistantRateLimiter {

    public static final int PER_MINUTE = 6;
    public static final int PER_DAY = 100;

    private final Cache<Long, AtomicInteger> minute = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    private final Cache<Long, AtomicInteger> day = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1)).maximumSize(100_000).build();

    /** 한 번 쓴 것으로 센다. 한도를 넘었으면 세지 않고 예외. */
    public synchronized void acquire(Long userId) {
        AtomicInteger m = minute.get(userId, k -> new AtomicInteger());
        AtomicInteger d = day.get(userId, k -> new AtomicInteger());
        if (d.get() >= PER_DAY) {
            throw new AssistantRateLimitedException("오늘은 AI 도우미에게 더 물어볼 수 없어요. 내일 다시 이용해 주세요.");
        }
        if (m.get() >= PER_MINUTE) {
            throw new AssistantRateLimitedException("너무 빨리 물어보고 있어요. 1분쯤 뒤에 다시 물어봐 주세요.");
        }
        m.incrementAndGet();
        d.incrementAndGet();
    }
}
