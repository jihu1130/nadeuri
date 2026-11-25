package com.example.nadeuri.user.service;

import com.example.nadeuri.user.domain.RememberMeToken;
import com.example.nadeuri.user.repository.RememberMeTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Spring Security가 "로그인 상태 유지" 토큰을 읽고 쓰는 저장소 (remember_me_token 테이블).
 * Spring이 말하는 username은 LoginUser.getUsername() = 우리 회원 id(숫자 문자열)다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RememberMeTokenStore implements PersistentTokenRepository {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final RememberMeTokenRepository repository;

    @Override
    public void createNewToken(PersistentRememberMeToken t) {
        repository.save(new RememberMeToken(t.getSeries(), Long.valueOf(t.getUsername()), t.getTokenValue(), toLocal(t.getDate())));
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        repository.findById(series).ifPresent(t -> t.renew(tokenValue, toLocal(lastUsed)));
    }

    @Override
    @Transactional(readOnly = true)
    public PersistentRememberMeToken getTokenForSeries(String series) {
        return repository.findById(series)
                .map(t -> new PersistentRememberMeToken(String.valueOf(t.getUserId()), t.getSeries(), t.getToken(),
                        Date.from(t.getLastUsed().atZone(ZONE).toInstant())))
                .orElse(null);
    }

    @Override
    public void removeUserTokens(String username) {
        try {
            removeAll(Long.valueOf(username));
        } catch (NumberFormatException ignored) {
            // 우리 회원 id가 아니면 지울 것도 없다
        }
    }

    /** 이 회원의 모든 기기에서 "로그인 상태 유지"를 끊는다 (비밀번호 변경·탈퇴) */
    public void removeAll(Long userId) {
        repository.deleteByUserId(userId);
    }

    private static LocalDateTime toLocal(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZONE);
    }
}
