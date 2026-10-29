-- 읽기 전용 데모 계정 + 예시 가계부 데이터 (ADR-069)
--
-- 사용법(운영 — 규민님이 SSH 에서 직접):
--   sudo mysql dotoree < demo-account.sql        (파일을 서버에 올린 뒤)
--   로컬:  mysql -u root -p dotoree < ops/demo-account.sql
-- 몇 번을 돌려도 된다 — 먼저 데모 계정의 옛 데이터를 지우고 다시 만든다(날짜는 "오늘" 기준 최근 6개월로 새로 깔린다)
-- 앱 설정 dotoree.demo.email 과 아래 @EMAIL 이 같아야 한다(기본 demo@dotoree.app)
--
-- 비밀번호: 아무도 모르는 값(무작위 문자열의 bcrypt 해시 — 원문은 만든 뒤 버렸다). 그래서 이 계정은 비밀번호로 못 들어오고
--           로그인 화면의 "데모로 둘러보기"(POST /api/auth/demo-login)만 된다
-- 만드는 것: 계좌 4개 · 카테고리(대분류 8 + 소분류 3) · 최근 약 180일 거래 · 이체(카드값 · 비상금)
-- 다른 사용자 데이터는 건드리지 않는다(전부 @U = 데모 계정 번호로만)

SET NAMES utf8mb4;
SET @EMAIL = 'demo@dotoree.app';
SET @PW    = '$2a$10$vPusFYpOCquYKfeKqcT6euCsveYYks3IQD8l4HAQI.7SDN/MBdrAG';

START TRANSACTION;

-- ── 1) 옛 데모 데이터 지우기 — UserService.purgeUser 와 같은 FK 순서(테이블이 늘면 거기부터 본다)
SET @OLD = (SELECT user_num FROM users WHERE user_email = @EMAIL);
DELETE FROM recurring_occurrences WHERE rule_num IN (SELECT rule_num FROM recurring_rules WHERE user_num = @OLD);
DELETE FROM recurring_rules       WHERE user_num = @OLD;
DELETE FROM personal_transactions WHERE user_num = @OLD;
DELETE FROM transfers             WHERE user_num = @OLD;
DELETE FROM balance_adjustments   WHERE user_num = @OLD;
DELETE FROM accounts              WHERE user_num = @OLD;
DELETE FROM personal_categories   WHERE user_num = @OLD AND parent_category_num IS NOT NULL;
DELETE FROM personal_categories   WHERE user_num = @OLD;
DELETE FROM refresh_tokens        WHERE user_num = @OLD;
DELETE FROM auth_verifications    WHERE email = @EMAIL;
DELETE FROM users                 WHERE user_num = @OLD;

-- ── 2) 계정
INSERT INTO users (user_pw, user_nickname, user_email, user_status)
VALUES (@PW, '데모', @EMAIL, 'A');
SET @U = LAST_INSERT_ID();

-- ── 3) 계좌 — 현금(기본) · 월급통장 · 비상금 · 신용카드. 시작 날짜는 거래가 시작되기 전날
SET @START = DATE_SUB(CURDATE(), INTERVAL 181 DAY);
INSERT INTO accounts (user_num, account_name, account_type, opening_balance, opening_date, is_default_yn) VALUES
    (@U, '현금',      'C',    50000, @START, 'Y'),
    (@U, '월급통장',  'B',  2000000, @START, 'N'),
    (@U, '비상금',    'B',  1000000, @START, 'N'),
    (@U, '신용카드',  'K',        0, @START, 'N');
SET @A_CASH = (SELECT account_num FROM accounts WHERE user_num = @U AND account_name = '현금');
SET @A_BANK = (SELECT account_num FROM accounts WHERE user_num = @U AND account_name = '월급통장');
SET @A_SAVE = (SELECT account_num FROM accounts WHERE user_num = @U AND account_name = '비상금');
SET @A_CARD = (SELECT account_num FROM accounts WHERE user_num = @U AND account_name = '신용카드');

-- ── 4) 카테고리 — '미분류'(기본) + 대분류 + 소분류(식비 아래)
INSERT INTO personal_categories (user_num, parent_category_num, category_name, category_emoji, category_type, is_default_yn) VALUES
    (@U, NULL, '미분류',     NULL, 'E', 'Y'),
    (@U, NULL, '미분류',     NULL, 'I', 'Y'),
    (@U, NULL, '식비',       '🍚', 'E', 'N'),
    (@U, NULL, '교통',       '🚌', 'E', 'N'),
    (@U, NULL, '쇼핑',       '🛍️', 'E', 'N'),
    (@U, NULL, '생활',       '🏠', 'E', 'N'),
    (@U, NULL, '문화/여가',  '🎬', 'E', 'N'),
    (@U, NULL, '의료',       '💊', 'E', 'N'),
    (@U, NULL, '월급',       '💰', 'I', 'N'),
    (@U, NULL, '용돈',       '🎁', 'I', 'N');
SET @C_FOOD = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '식비');
INSERT INTO personal_categories (user_num, parent_category_num, category_name, category_emoji, category_type, is_default_yn) VALUES
    (@U, @C_FOOD, '점심',   '🍱', 'E', 'N'),
    (@U, @C_FOOD, '카페',   '☕', 'E', 'N'),
    (@U, @C_FOOD, '장보기', '🛒', 'E', 'N');
SET @C_LUNCH  = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '점심');
SET @C_CAFE   = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '카페');
SET @C_GROC   = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '장보기');
SET @C_BUS    = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '교통');
SET @C_SHOP   = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '쇼핑');
SET @C_LIFE   = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '생활');
SET @C_FUN    = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '문화/여가');
SET @C_MED    = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '의료');
SET @C_SALARY = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '월급');
SET @C_ALLOW  = (SELECT category_num FROM personal_categories WHERE user_num = @U AND category_name = '용돈');

-- ── 5) 거래 — 오늘부터 179일 전까지 하루씩(i). 금액은 i 로 정한 규칙이라 다시 돌려도 같은 모양
--    평일(월~금)은 점심 · 교통, 이틀에 한 번 카페, 아흐레마다 장보기 … 카드값 · 월세 · 월급은 날짜(일)로
INSERT INTO personal_transactions (user_num, category_num, account_num, trans_type, trans_amount, trans_date, trans_memo)
WITH RECURSIVE n (i) AS (
    SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 179
), d AS (
    SELECT i, DATE_SUB(CURDATE(), INTERVAL i DAY) AS day FROM n
)
SELECT @U, @C_LUNCH, @A_CARD, 'E', 8000 + (i * 37 % 5) * 1000, day, '점심' FROM d WHERE DAYOFWEEK(day) BETWEEN 2 AND 6
UNION ALL
SELECT @U, @C_CAFE, @A_CARD, 'E', 4500 + (i % 3) * 500, day, '커피' FROM d WHERE i % 2 = 0
UNION ALL
SELECT @U, @C_GROC, @A_CARD, 'E', 25000 + (i * 13 % 7) * 5000, day, '마트' FROM d WHERE i % 9 = 0
UNION ALL
SELECT @U, @C_BUS, @A_CARD, 'E', 2800, day, '지하철' FROM d WHERE DAYOFWEEK(day) BETWEEN 2 AND 6
UNION ALL
SELECT @U, @C_SHOP, @A_CARD, 'E', 30000 + (i % 5) * 15000, day, '옷 · 잡화' FROM d WHERE i % 23 = 3
UNION ALL
SELECT @U, @C_FUN, @A_CARD, 'E', 15000 + (i % 4) * 5000, day, '영화 · 취미' FROM d WHERE i % 17 = 5
UNION ALL
SELECT @U, @C_MED, @A_CARD, 'E', 12000 + (i % 3) * 6000, day, '병원 · 약국' FROM d WHERE i % 41 = 7
UNION ALL
SELECT @U, @C_LIFE, @A_BANK, 'E', 500000, day, '월세' FROM d WHERE DAY(day) = 5
UNION ALL
SELECT @U, @C_LIFE, @A_BANK, 'E', 85000, day, '관리비 · 통신비' FROM d WHERE DAY(day) = 20
UNION ALL
SELECT @U, @C_SALARY, @A_BANK, 'I', 2800000, day, '월급' FROM d WHERE DAY(day) = 25
UNION ALL
SELECT @U, @C_ALLOW, @A_CASH, 'I', 100000, day, '용돈' FROM d WHERE i % 61 = 10;

-- ── 6) 이체 — 카드값(월급통장 → 신용카드, 매달 10일) · 비상금 저축(월급통장 → 비상금, 매달 26일). 수입 · 지출이 아니라 통계에 안 잡힌다
INSERT INTO transfers (user_num, from_account_num, to_account_num, transfer_amount, transfer_date, transfer_memo)
WITH RECURSIVE n (i) AS (
    SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 179
), d AS (
    SELECT DATE_SUB(CURDATE(), INTERVAL i DAY) AS day FROM n
)
SELECT @U, @A_BANK, @A_CARD, 600000, day, '카드값' FROM d WHERE DAY(day) = 10
UNION ALL
SELECT @U, @A_BANK, @A_SAVE, 300000, day, '비상금 모으기' FROM d WHERE DAY(day) = 26;

COMMIT;

-- ── 7) 확인 — 계좌 4 · 카테고리 13 · 거래 수백 건 · 이체 약 12건
SELECT '계좌' AS 항목, COUNT(*) AS 건수 FROM accounts WHERE user_num = @U
UNION ALL SELECT '카테고리', COUNT(*) FROM personal_categories WHERE user_num = @U
UNION ALL SELECT '거래', COUNT(*) FROM personal_transactions WHERE user_num = @U
UNION ALL SELECT '이체', COUNT(*) FROM transfers WHERE user_num = @U;
