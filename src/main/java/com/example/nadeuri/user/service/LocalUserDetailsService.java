package com.example.nadeuri.user.service;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디 로그인 폼을 제출하면 Spring Security가 호출한다.
 * 아이디로 우리 사이트 회원을 찾아 돌려주면, 비밀번호 비교는 Spring Security가 한다.
 * 카카오로 가입한 회원은 비밀번호가 없으므로 여기서 찾지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LocalUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = repository.findByProviderAndProviderId(User.PROVIDER_LOCAL, SignUpRequest.normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("가입되지 않은 아이디"));
        return new LoginUser(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getPassword());
    }
}
