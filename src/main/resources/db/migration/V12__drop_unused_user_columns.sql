-- 안 쓰는 표 · 칸 정리 ② (ADR-070) — users 의 안 쓰는 5칸
--
-- V11 배포에서 코드(INSERT · User 필드)가 이 칸들을 안 쓰게 됐다 — 그래서 이 배포의 옛 jar(.prev)도 이 칸을 모른다(ADR-053 의 두 번째 배포)
--   user_id      유저 검색용 공개 핸들 — 검색 기능 계획 없음 (ADR-013 폐기). UNIQUE 인덱스(UK_USER_ID)는 칸과 함께 사라진다
--   user_phone · user_birth · signup_ip   넣는 곳이 없어 항상 NULL
--   book_open_yn   공동 가계부 공개 여부 — 공동 가계부 기획 폐기
-- 되돌릴 수 없다(DROP) — 값이 없는 칸이고, 매일 04:00 백업이 있다

ALTER TABLE users
    DROP COLUMN user_id,
    DROP COLUMN user_phone,
    DROP COLUMN user_birth,
    DROP COLUMN book_open_yn,
    DROP COLUMN signup_ip;
