package com.example.nadeuri.user.service;

import com.example.nadeuri.gathering.service.GatheringService;
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
    private final GatheringService gatheringService;

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

    public User get(Long userId) {
        return repository.findById(userId).orElseThrow(() -> new IllegalStateException("이미 탈퇴한 회원이에요."));
    }

    /**
     * 회원 탈퇴. 만든 모임은 지우고, 참여한 모임에서는 빠진 뒤 회원 정보를 지운다.
     * 아이디 회원은 비밀번호를 한 번 더 확인한다 (카카오 회원은 비밀번호가 없음).
     *
     * @throws IllegalArgumentException 비밀번호가 맞지 않음
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = get(userId);
        if (user.isLocal() && (password == null || !passwordEncoder.matches(password, user.getPassword()))) {
            throw new IllegalArgumentException("비밀번호가 맞지 않아요.");
        }
        gatheringService.removeAllFor(userId);
        repository.delete(user);
    }

    @Transactional
    public void markLoggedIn(Long userId) {
        repository.findById(userId).ifPresent(User::markLoggedIn);
    }
}
