package com.example.nadeuri.user.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 회원. 가입 경로(provider)와 그 경로에서의 식별값(providerId) 조합이 유일하다.
 * - 카카오:  provider = "kakao", providerId = 카카오 회원번호, password 없음
 * - 우리 사이트: provider = "local", providerId = 아이디(소문자), password = BCrypt 해시
 * ("user"는 H2·일부 DB의 예약어라 테이블 이름은 users)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = "uk_users_provider", columnNames = {"provider", "provider_id"}))
public class User {

    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_KAKAO = "kakao";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    /** 우리 사이트 회원만 값이 있다. 평문이 아닌 BCrypt 해시 ({bcrypt}... 형식) */
    @Column(length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    /** 소셜 로그인 회원 */
    public User(String provider, String providerId, String nickname, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = Times.now();
        this.lastLoginAt = this.createdAt;
    }

    /** 우리 사이트(아이디 + 비밀번호) 회원. passwordHash는 반드시 PasswordEncoder로 만든 값 */
    public static User local(String username, String passwordHash, String nickname) {
        User u = new User(PROVIDER_LOCAL, username, nickname, null);
        u.password = passwordHash;
        return u;
    }

    public boolean isLocal() {
        return PROVIDER_LOCAL.equals(provider);
    }

    /** 로그인할 때마다 소셜 프로필(닉네임, 사진)을 최신으로 맞춘다 */
    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.lastLoginAt = Times.now();
    }

    public void markLoggedIn() {
        this.lastLoginAt = Times.now();
    }
}
