package com.example.nadeuri.user.service;

import com.example.nadeuri.user.dto.SignUpRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 아이디 로그인 시도 제한.
 * 같은 아이디로 비밀번호를 MAX_FAILURES번 틀리면 LOCK_TIME 동안 그 아이디로는 로그인할 수 없다
 * (비밀번호를 계속 바꿔 넣어 보는 공격을 막기 위함).
 * 마지막 실패 후 LOCK_TIME이 지나면 기록이 사라지고, 로그인에 성공해도 지운다.
 * 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> failures = Caffeine.newBuilder()
            .expireAfterWrite(LOCK_TIME)
            .maximumSize(100_000)
            .build();

    public boolean isBlocked(String username) {
        AtomicInteger count = failures.getIfPresent(key(username));
        return count != null && count.get() >= MAX_FAILURES;
    }

    public void recordFailure(String username) {
        String key = key(username);
        AtomicInteger count = failures.get(key, k -> new AtomicInteger());
        count.incrementAndGet();
        failures.put(key, count); // 실패할 때마다 잠금 시간을 다시 센다
    }

    public void recordSuccess(String username) {
        failures.invalidate(key(username));
    }

    private static String key(String username) {
        String normalized = SignUpRequest.normalizeUsername(username);
        return normalized == null ? "" : normalized;
    }
}
