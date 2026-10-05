-- 전체 초기화 : 유저 포함 모든 데이터 삭제
-- (Flyway 이력 flyway_schema_history 는 건드리지 않으므로 마이그레이션 재실행 없음)
--
-- 사용법:
--   mysql -u root -p dotoree < qa/reset-all.sql
--   또는 mysql 접속 후:  source ~/git/dotoree/qa/reset-all.sql
--
-- 실행 후 반드시 계정을 다시 만들 것.
-- 회원가입 API 를 통해야 '미분류' 카테고리(E/I)가 자동 생성된다.
-- SQL 로 유저만 직접 INSERT 하면 미분류가 없어서 거래 이관이 깨진다.

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE personal_transactions;
TRUNCATE TABLE personal_categories;
TRUNCATE TABLE refresh_tokens;
TRUNCATE TABLE auth_verifications;
TRUNCATE TABLE social_accounts;
TRUNCATE TABLE ai_usage_logs;
TRUNCATE TABLE receipt_analysis;
TRUNCATE TABLE merchant_profiles;
TRUNCATE TABLE expense_logs;
TRUNCATE TABLE settlement_snapshots;
TRUNCATE TABLE group_transactions;
TRUNCATE TABLE group_periods;
TRUNCATE TABLE group_merchant_profiles;
TRUNCATE TABLE group_categories;
TRUNCATE TABLE group_members;
TRUNCATE TABLE invitations;
TRUNCATE TABLE `groups`;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '── 초기화 완료. 남은 유저 ──' AS '';
SELECT COUNT(*) AS 유저수 FROM users;
SELECT COUNT(*) AS 카테고리수 FROM personal_categories;
SELECT COUNT(*) AS 거래수 FROM personal_transactions;
