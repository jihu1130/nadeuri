-- 채팅 메시지 삭제(보낸 사람만). 줄은 남기고 내용은 비운 뒤 지운 시각을 적는다
-- → 모두의 화면에 "삭제된 메시지예요"로 보이고, 날짜 구분·이전 메시지 불러오기 순서는 그대로 유지된다.
ALTER TABLE chat_message
    ADD COLUMN deleted_at DATETIME(6) NULL;
