package com.example.nadeuri.user.service;

import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.KakaoProfile;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.exception.DuplicateUsernameException;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    /** 카카오 로그인 성공 시: 처음이면 가입, 이미 있으면 프로필만 갱신 */
    @Transactional
    public User loginWithKakao(KakaoProfile profile) {
        return repository.findByProviderAndProviderId(User.PROVIDER_KAKAO, profile.id())
                .map(u -> {
                    u.updateProfile(profile.nickname(), profile.profileImageUrl());
                    return u;
                })
                .orElseGet(() -> repository.save(
                        new User(User.PROVIDER_KAKAO, profile.id(), profile.nickname(), profile.profileImageUrl())));
    }

    /**
     * 우리 사이트(아이디 + 비밀번호) 회원가입. 비밀번호는 BCrypt로 해시해서 저장한다.
     * 같은 아이디로 동시에 가입하면 (provider, provider_id) 유니크 제약이 막는다.
     *
     * @throws DuplicateUsernameException 이미 사용 중인 아이디
     */
    @Transactional
    public User signUp(SignUpRequest req) {
        String username = SignUpRequest.normalizeUsername(req.username());
        if (repository.findByProviderAndProviderId(User.PROVIDER_LOCAL, username).isPresent()) {
            throw new DuplicateUsernameException();
        }
        try {
            return repository.saveAndFlush(
                    User.local(username, passwordEncoder.encode(req.password()), req.nickname().strip()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException();
        }
    }

    @Transactional
    public void markLoggedIn(Long userId) {
        repository.findById(userId).ifPresent(User::markLoggedIn);
    }
}
