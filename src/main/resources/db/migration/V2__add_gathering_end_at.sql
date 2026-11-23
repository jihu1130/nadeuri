-- 모임 끝나는 시간 추가 (2026-11-23)
-- 기존 모임은 끝나는 시간을 모르므로 "시작 + 2시간"으로 채운 뒤 NOT NULL로 바꾼다.

ALTER TABLE gathering ADD COLUMN end_at DATETIME(6) NULL AFTER start_at;

UPDATE gathering SET end_at = DATE_ADD(start_at, INTERVAL 2 HOUR) WHERE end_at IS NULL;

ALTER TABLE gathering MODIFY end_at DATETIME(6) NOT NULL;   -- 한국 시간(KST)
