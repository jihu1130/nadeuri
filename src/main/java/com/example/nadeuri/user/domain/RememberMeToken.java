package com.example.nadeuri.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "로그인 상태 유지" 토큰 한 줄. 브라우저 하나(기기 하나)마다 series가 하나씩 생긴다.
 * 쿠키를 훔쳐 쓴 흔적(같은 series인데 token이 다름)이 보이면 Spring Security가 그 회원의 토큰을 모두 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "remember_me_token", indexes = @Index(name = "idx_remember_me_token_user", columnList = "user_id"))
public class RememberMeToken {

    @Id
    @Column(length = 64)
    private String series;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;

    public RememberMeToken(String series, Long userId, String token, LocalDateTime lastUsed) {
        this.series = series;
        this.userId = userId;
        this.token = token;
        this.lastUsed = lastUsed;
    }

    public void renew(String token, LocalDateTime lastUsed) {
        this.token = token;
        this.lastUsed = lastUsed;
    }
}
