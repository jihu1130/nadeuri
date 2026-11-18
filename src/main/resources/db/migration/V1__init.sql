-- 나들이 첫 스키마 (MySQL 8). Flyway가 빈 DB에 처음 한 번 실행한다.
-- Flyway 도입 전부터 쓰던 DB는 이 파일을 실행하지 않고 "이미 V1"로 기록만 한다 (baseline-on-migrate).
-- 테이블을 바꿀 때는 이 파일을 고치지 말고 V2__설명.sql 처럼 새 파일을 추가한다.

CREATE TABLE gathering (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(100)  NOT NULL,
    description    VARCHAR(1000) NULL,
    tag            ENUM ('BOARD_GAME','CAFE','FESTIVAL','PET','PHOTO','PICNIC','RUNNING','WALK') NOT NULL,
    place_name     VARCHAR(255)  NOT NULL,
    latitude       DOUBLE        NOT NULL,
    longitude      DOUBLE        NOT NULL,
    start_at       DATETIME(6)   NOT NULL,   -- 한국 시간(KST)
    max_people     INT           NOT NULL,
    current_people INT           NOT NULL,   -- 주최자 포함, 원자적 UPDATE로만 바꾼다
    created_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_gathering_lat_lng (latitude, longitude)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE participant (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    gathering_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,       -- users.id (외래키 없음)
    host         BIT(1)      NOT NULL,
    joined_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_gathering_user (gathering_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(20)  NOT NULL,  -- local | kakao
    provider_id       VARCHAR(64)  NOT NULL,  -- 아이디(소문자) 또는 카카오 회원번호
    password          VARCHAR(100) NULL,      -- BCrypt 해시, 우리 사이트 회원만
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    created_at        DATETIME(6)  NULL,
    last_login_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_provider (provider, provider_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
