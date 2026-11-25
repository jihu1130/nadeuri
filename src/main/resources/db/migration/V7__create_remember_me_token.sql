-- "로그인 상태 유지" 토큰 (Spring Security PersistentTokenBasedRememberMeServices).
-- 브라우저 쿠키에는 series:token 만 있고, 여기 저장된 값과 맞아야 자동 로그인된다.
-- 자동 로그인할 때마다 token이 바뀌고, 로그아웃·비밀번호 변경·탈퇴 시 그 회원의 줄을 지운다.
CREATE TABLE remember_me_token
(
    series    VARCHAR(64) NOT NULL,
    user_id   BIGINT      NOT NULL,
    token     VARCHAR(64) NOT NULL,
    last_used DATETIME(6) NOT NULL,
    PRIMARY KEY (series),
    INDEX idx_remember_me_token_user (user_id)
);
