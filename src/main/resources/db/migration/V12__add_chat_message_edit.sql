-- 채팅 메시지 수정 (보낸 사람만). 메시지에는 마지막으로 고친 시각을 적고,
-- 고치기 전 내용은 chat_message_revision에 한 줄씩 남겨 "수정됨"을 누르면 누구나 볼 수 있게 한다.
-- 메시지를 지우면 수정 기록도 함께 지운다 (ChatService.delete, GatheringService 모임 삭제).
ALTER TABLE chat_message
    ADD COLUMN edited_at DATETIME(6) NULL;

CREATE TABLE chat_message_revision (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    message_id BIGINT       NOT NULL,   -- chat_message.id
    content    VARCHAR(500) NOT NULL,   -- 고치기 전 내용
    written_at DATETIME(6)  NOT NULL,   -- 그 내용이 쓰인 시각 (처음 보낸 시각 또는 앞선 수정 시각, KST)
    PRIMARY KEY (id),
    KEY idx_chat_message_revision_message (message_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
