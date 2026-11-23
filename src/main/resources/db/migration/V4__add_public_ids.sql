-- 주소에 쓰는 공개 식별자(UUID) 추가 (2026-11-23)
-- 주소에 1, 2, 3 같은 순번이 보이면 모임·회원 수를 짐작하거나 다른 번호를 넣어 볼 수 있어서,
-- 화면·API·채팅 주소에는 무작위 UUID(public_id)를 쓰고 순번(id)은 DB 안에서만 쓴다.
-- 기존 행은 무작위 UUID(버전 4 형식)로 채운다. MySQL UUID()는 시간·장비 정보가 들어가는 버전 1이라 쓰지 않는다.

ALTER TABLE gathering ADD COLUMN public_id VARCHAR(36) NULL AFTER id;
UPDATE gathering SET public_id = LOWER(CONCAT(
        HEX(RANDOM_BYTES(4)), '-',
        HEX(RANDOM_BYTES(2)), '-',
        '4', SUBSTR(HEX(RANDOM_BYTES(2)), 2), '-',
        HEX(FLOOR(ASCII(RANDOM_BYTES(1)) / 64) + 8), SUBSTR(HEX(RANDOM_BYTES(2)), 2), '-',
        HEX(RANDOM_BYTES(6))))
 WHERE public_id IS NULL;
ALTER TABLE gathering MODIFY public_id VARCHAR(36) NOT NULL;
ALTER TABLE gathering ADD CONSTRAINT uk_gathering_public_id UNIQUE (public_id);

ALTER TABLE users ADD COLUMN public_id VARCHAR(36) NULL AFTER id;
UPDATE users SET public_id = LOWER(CONCAT(
        HEX(RANDOM_BYTES(4)), '-',
        HEX(RANDOM_BYTES(2)), '-',
        '4', SUBSTR(HEX(RANDOM_BYTES(2)), 2), '-',
        HEX(FLOOR(ASCII(RANDOM_BYTES(1)) / 64) + 8), SUBSTR(HEX(RANDOM_BYTES(2)), 2), '-',
        HEX(RANDOM_BYTES(6))))
 WHERE public_id IS NULL;
ALTER TABLE users MODIFY public_id VARCHAR(36) NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_public_id UNIQUE (public_id);
