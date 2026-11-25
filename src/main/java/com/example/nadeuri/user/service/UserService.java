package com.example.nadeuri.user.service;

import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SocialProfile;
import com.example.nadeuri.user.dto.PasswordChangeRequest;
import com.example.nadeuri.user.dto.ProfileUpdateRequest;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.exception.DuplicateUsernameException;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final GatheringService gatheringService;
    private final ProfileImageStorage profileImageStorage;

    /** 소셜 로그인(카카오, 구글) 성공 시: 처음이면 가입, 이미 있으면 프로필만 갱신 */
    @Transactional
    public User loginWithSocial(SocialProfile profile) {
        return repository.findByProviderAndProviderId(profile.provider(), profile.providerId())
                .map(u -> {
                    u.updateProfile(profile.nickname(), profile.profileImageUrl());
                    return u;
                })
                .orElseGet(() -> repository.save(new User(
                        profile.provider(), profile.providerId(), profile.nickname(), profile.profileImageUrl())));
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
        profileImageStorage.deleteIfOwned(user.getProfileImageUrl());
    }

    /** 프로필 설정: 닉네임·자기소개 (앞뒤 공백은 지운다) */
    @Transactional
    public User updateProfile(Long userId, ProfileUpdateRequest req) {
        User user = get(userId);
        user.changeProfile(req.nickname().strip(), req.bio() == null ? null : req.bio().strip());
        return user;
    }

    /**
     * 프로필 사진 바꾸기. 새 사진을 먼저 저장하고, 예전에 올린 사진 파일은 지운다.
     * @throws IllegalArgumentException 그림이 아니거나 너무 큰 파일
     */
    @Transactional
    public User changePhoto(Long userId, MultipartFile file) {
        User user = get(userId);
        String old = user.getProfileImageUrl();
        user.changePhoto(profileImageStorage.store(file));
        profileImageStorage.deleteIfOwned(old);
        return user;
    }

    /** 프로필 사진 지우기 → 첫 글자 동그라미로 보인다 (카카오·구글 사진으로 다시 바뀌지 않음) */
    @Transactional
    public User removePhoto(Long userId) {
        User user = get(userId);
        String old = user.getProfileImageUrl();
        user.changePhoto(null);
        profileImageStorage.deleteIfOwned(old);
        return user;
    }

    /**
     * 비밀번호 바꾸기 (아이디 회원만).
     * @throws IllegalArgumentException 지금 비밀번호가 틀림, 새 비밀번호 두 칸이 다름, 지금과 같은 비밀번호
     * @throws IllegalStateException    카카오·구글 회원
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest req) {
        User user = get(userId);
        if (!user.isLocal()) {
            throw new IllegalStateException("카카오·구글 회원은 비밀번호가 없어요.");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("지금 비밀번호가 맞지 않아요.");
        }
        if (!req.passwordsMatch()) {
            throw new IllegalArgumentException("새 비밀번호 두 칸이 서로 달라요.");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new IllegalArgumentException("지금과 다른 비밀번호로 정해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
    }

    @Transactional
    public void markLoggedIn(Long userId) {
        repository.findById(userId).ifPresent(User::markLoggedIn);
    }
}
