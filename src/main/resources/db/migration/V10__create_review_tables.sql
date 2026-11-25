-- 끝난 모임 후기 (2026-11-25)
-- gathering_review: 참여자가 끝난 모임에 남기는 별점(1~5)과 한 줄 후기. 한 사람이 한 모임에 하나.
-- meet_again: 같은 모임 참여자에게 보내는 "다음에 또 만나요" (좋은 표시만 있고 나쁜 표시는 없다).
-- 다른 표처럼 외래키는 두지 않는다. 모임을 지우면 GatheringService가, 탈퇴하면 UserService가 함께 지운다.

CREATE TABLE gathering_review (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    gathering_id BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,   -- 후기를 쓴 사람 users.id
    rating       INT          NOT NULL,   -- 1~5
    content      VARCHAR(200) NULL,
    created_at   DATETIME(6)  NOT NULL,   -- 한국 시간(KST)
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_gathering_user (gathering_id, user_id),
    KEY idx_review_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE meet_again (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    gathering_id BIGINT      NOT NULL,
    from_user_id BIGINT      NOT NULL,
    to_user_id   BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meet_again (gathering_id, from_user_id, to_user_id),
    KEY idx_meet_again_to (to_user_id),
    KEY idx_meet_again_from (from_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
