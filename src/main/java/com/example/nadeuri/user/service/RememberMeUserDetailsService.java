package com.example.nadeuri.user.service;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "로그인 상태 유지" 쿠키로 자동 로그인할 때 회원을 다시 불러온다.
 * 토큰에는 회원 id가 들어 있으므로 아이디가 아니라 id로 찾는다 (탈퇴한 회원이면 자동 로그인 안 됨).
 * 닉네임·사진은 지금 DB 값으로 새로 채운다.
 */
@Service
@RequiredArgsConstructor
public class RememberMeUserDetailsService {

    private final UserRepository repository;

    @Transactional(readOnly = true)
    public UserDetails loadById(String id) {
        try {
            User user = repository.findById(Long.valueOf(id))
                    .orElseThrow(() -> new UsernameNotFoundException("탈퇴한 회원"));
            return new LoginUser(user.getId(), user.getNickname(), user.getProfileImageUrl());
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("잘못된 회원 번호");
        }
    }
}
