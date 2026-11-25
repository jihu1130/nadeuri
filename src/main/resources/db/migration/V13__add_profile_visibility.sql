-- 프로필의 활동 기록 공개 범위 (2026-11-25)
-- 다른 사람이 내 프로필(/users/{publicId})에서 볼 수 있는 범위를 항목마다 고른다.
--   PUBLIC  = 누구나
--   MEMBERS = 나와 같은 모임에 함께한 적 있는 사람만
--   PRIVATE = 나만
-- hosted_visibility: 내가 연 모임 (기본 PUBLIC. 모임은 원래 지도에 공개돼 있다)
-- joined_visibility: 내가 참여한 끝난 모임 (기본 MEMBERS. 다녀온 곳이 모여 보이므로 조심스럽게)
ALTER TABLE users
    ADD COLUMN hosted_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN joined_visibility VARCHAR(20) NOT NULL DEFAULT 'MEMBERS';
