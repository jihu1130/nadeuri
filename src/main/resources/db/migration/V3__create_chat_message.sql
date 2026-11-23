-- 모임 채팅 메시지 (2026-11-23)
-- 모임(gathering)·회원(users)과 외래키로 묶지 않는다 (participant 테이블과 같은 방식).
-- 모임을 지울 때는 GatheringService.delete가 메시지도 함께 지운다.

CREATE TABLE chat_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    gathering_id BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,   -- 보낸 사람 users.id
    content      VARCHAR(500) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,   -- 한국 시간(KST)
    PRIMARY KEY (id),
    KEY idx_chat_message_gathering (gathering_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
