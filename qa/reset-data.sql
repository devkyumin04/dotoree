-- QA 데이터 정리
-- USERS 는 남기고, 거래 전체 + 사용자가 만든 카테고리만 삭제한다.
--
-- 사용법:
--   mysql -u root -p <스키마명> < qa/reset-data.sql
--
-- 주의: '미분류'(is_default_yn = 'Y')는 삭제하지 않는다.
--       가입 시 자동 생성되는 기본 카테고리라, 지우면
--       거래 등록과 카테고리 삭제 이관이 전부 깨진다.

-- 1) 거래 전체 삭제 — 카테고리를 참조(FK)하므로 반드시 먼저
DELETE FROM recurring_occurrences;
DELETE FROM recurring_rules;
DELETE FROM transfers;
DELETE FROM balance_adjustments;
DELETE FROM personal_transactions;
ALTER TABLE personal_transactions AUTO_INCREMENT = 1;

-- 2) 사용자 생성 카테고리만 삭제 ('미분류' 유지)
--    자기참조 FK(FK_PCAT_PARENT) 때문에 한 문장으로 지우면 1451 이 난다.
--    MySQL 이 행 삭제 순서를 보장하지 않아 부모가 먼저 지워지는 순간
--    자식이 아직 그 부모를 참조하기 때문. 소분류 → 대분류 순서로 나눈다.
--    (카테고리는 2계층 제한이라 두 번이면 충분 — ADR-019)
DELETE FROM accounts WHERE is_default_yn = 'N';
DELETE FROM personal_categories WHERE is_default_yn = 'N' AND parent_category_num IS NOT NULL;
DELETE FROM personal_categories WHERE is_default_yn = 'N';

-- 3) 결과 확인
SELECT '── 남은 카테고리 ──' AS '';
SELECT category_num, user_num, category_name, category_type, is_default_yn
FROM personal_categories ORDER BY user_num, category_num;

SELECT '── 남은 거래 ──' AS '';
SELECT COUNT(*) AS 거래건수 FROM personal_transactions;

SELECT '── 유저 ──' AS '';
SELECT user_num, user_email, user_nickname, user_status FROM users;
