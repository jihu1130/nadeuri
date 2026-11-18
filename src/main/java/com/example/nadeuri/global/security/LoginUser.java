package com.example.nadeuri.global.security;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 로그인한 사용자 (세션에 저장되는 인증 정보).
 * 컨트롤러에서 {@code @AuthenticationPrincipal LoginUser user}로 받는다.
 * id는 우리 서비스의 users.id (카카오 회원번호나 로그인 아이디가 아님).
 *
 * 소셜 로그인(OAuth2User)과 아이디 로그인(UserDetails) 모두 이 클래스 하나로 다룬다.
 * 비밀번호 해시는 로그인 검사에만 쓰고, 검사가 끝나면 Spring Security가
 * eraseCredentials()를 불러 지우므로 세션에는 남지 않는다.
 */
@Getter
public class LoginUser implements OAuth2User, UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String nickname;
    private final String profileImageUrl;
    private String password;

    public LoginUser(Long id, String nickname, String profileImageUrl) {
        this(id, nickname, profileImageUrl, null);
    }

    public LoginUser(Long id, String nickname, String profileImageUrl, String passwordHash) {
        this.id = id;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.password = passwordHash;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("id", id);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
