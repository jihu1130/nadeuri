-- 활동 종류에 '기타'(OTHER) 추가. tag 열이 MySQL ENUM이라 허용 값 목록을 새로 지정한다.
ALTER TABLE gathering
    MODIFY tag ENUM ('BOARD_GAME','CAFE','FESTIVAL','OTHER','PET','PHOTO','PICNIC','RUNNING','WALK') NOT NULL;
