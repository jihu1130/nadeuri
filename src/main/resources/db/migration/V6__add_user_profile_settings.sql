-- 프로필 설정: 자기소개 한 줄, 그리고 우리 사이트에서 직접 바꾼 닉네임·사진은
-- 카카오·구글로 다시 로그인해도 덮어쓰지 않도록 표시해 두는 칸.
ALTER TABLE users
    ADD COLUMN bio                 VARCHAR(100) NULL,
    ADD COLUMN nickname_customized BIT(1)       NOT NULL DEFAULT 0,
    ADD COLUMN photo_customized    BIT(1)       NOT NULL DEFAULT 0;
