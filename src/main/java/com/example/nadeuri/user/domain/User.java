package com.example.nadeuri.user.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 서비스 회원. 가입 경로(provider)와 그 경로에서의 식별값(providerId) 조합이 유일하다.
 * - 카카오:  provider = "kakao", providerId = 카카오 회원번호, password 없음
 * - 구글:    provider = "google", providerId = 구글 sub(계정 고유번호), password 없음
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
    public static final String PROVIDER_GOOGLE = "google";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 화면·채팅·(나중에) 프로필 주소에 쓰는 공개 식별자 (무작위 UUID). 순번 id는 DB 안에서만 쓴다 */
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = UUID.randomUUID().toString();

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

    /** 자기소개 한 줄 (참여자 목록에 보임). 없으면 null */
    @Column(length = BIO_MAX)
    private String bio;

    /** 우리 사이트에서 닉네임을 직접 바꿨으면 true → 소셜 로그인 때 소셜 닉네임으로 덮어쓰지 않는다 */
    @Column(name = "nickname_customized", nullable = false)
    private boolean nicknameCustomized;

    /** 우리 사이트에서 사진을 올리거나 지웠으면 true → 소셜 로그인 때 소셜 사진으로 덮어쓰지 않는다 */
    @Column(name = "photo_customized", nullable = false)
    private boolean photoCustomized;

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

    /**
     * 소셜 로그인할 때마다 소셜 프로필(닉네임, 사진)을 최신으로 맞춘다.
     * 단, 우리 사이트에서 직접 바꾼 항목은 그대로 둔다.
     */
    public void updateProfile(String nickname, String profileImageUrl) {
        if (!nicknameCustomized) this.nickname = nickname;
        if (!photoCustomized) this.profileImageUrl = profileImageUrl;
        this.lastLoginAt = Times.now();
    }

    public static final int NICKNAME_MAX = 20;
    public static final int BIO_MAX = 100;

    /** 프로필 설정: 닉네임·자기소개 바꾸기 (자기소개가 비어 있으면 지운다) */
    public void changeProfile(String nickname, String bio) {
        if (!nickname.equals(this.nickname)) {
            this.nickname = nickname;
            this.nicknameCustomized = true;
        }
        this.bio = (bio == null || bio.isBlank()) ? null : bio;
    }

    /** 프로필 사진 바꾸기. null이면 사진을 지운다 (첫 글자 동그라미로 표시) */
    public void changePhoto(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.photoCustomized = true;
    }

    /** 비밀번호 바꾸기 (우리 사이트 회원만). passwordHash는 반드시 PasswordEncoder로 만든 값 */
    public void changePassword(String passwordHash) {
        if (!isLocal()) throw new IllegalStateException("카카오·구글 회원은 비밀번호가 없어요.");
        this.password = passwordHash;
    }

    public void markLoggedIn() {
        this.lastLoginAt = Times.now();
    }
}
