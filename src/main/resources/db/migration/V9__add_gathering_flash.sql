-- 번개 모임: 저장하는 때부터 2시간 안에 시작하는 즉석 모임 표시.
-- 지도·목록에서 번쩍이는 핀과 "번개" 배지로 따로 보여 준다. 기존 모임은 모두 일반 모임(0).
ALTER TABLE gathering
    ADD COLUMN flash BIT(1) NOT NULL DEFAULT 0;
