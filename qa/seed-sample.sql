-- 통계 개발·인덱스 실습용 더미 데이터
--
-- 사용법:  mysql -u root -p dotoree < qa/seed-sample.sql
-- 전제:    test@test.com 계정이 이미 있어야 한다 (미분류 E/I 포함)
--          없으면 먼저  sh qa/reset-all.sh
--
-- 만드는 것
--   · 대분류 5개 + 소분류 14개 (지출), 수입 대분류 2개 + 소분류 2개
--   · 최근 6개월치 거래 약 4,000건 (날짜·금액·카테고리 랜덤)
--   · 대분류에 직접 달린 거래도 섞음 (소분류뷰 '기타' 케이스 확인용)
--
-- 다시 돌리려면 먼저  mysql -u root -p dotoree < qa/reset-data.sql

SET @U = (SELECT user_num FROM users WHERE user_email = 'test@test.com');

-- ── 1) 카테고리 ───────────────────────────────────────────────
-- 대분류
INSERT INTO personal_categories (user_num, parent_category_num, category_name, category_emoji, category_type)
VALUES (@U, NULL, '식비',   '🍚', 'E'),
       (@U, NULL, '교통',   '🚌', 'E'),
       (@U, NULL, '주거',   '🏠', 'E'),
       (@U, NULL, '문화',   '🎬', 'E'),
       (@U, NULL, '의료',   '💊', 'E'),
       (@U, NULL, '급여',   '💰', 'I'),
       (@U, NULL, '용돈',   '🎁', 'I');

SET @C_FOOD    = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='식비');
SET @C_TRANS   = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='교통');
SET @C_HOME    = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='주거');
SET @C_CULTURE = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='문화');
SET @C_MED     = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='의료');
SET @C_PAY     = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='급여');
SET @C_GIFT    = (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='용돈');

-- 소분류 (식비 아래를 촘촘하게 — 실제 사용 패턴)
INSERT INTO personal_categories (user_num, parent_category_num, category_name, category_emoji, category_type)
VALUES (@U, @C_FOOD,    '닭가슴살', '🍗', 'E'),
       (@U, @C_FOOD,    '프로틴',   '🥤', 'E'),
       (@U, @C_FOOD,    '햇반',     '🍙', 'E'),
       (@U, @C_FOOD,    '편의점',   '🏪', 'E'),
       (@U, @C_FOOD,    '카페',     '☕', 'E'),
       (@U, @C_FOOD,    '배달',     '🛵', 'E'),
       (@U, @C_TRANS,   '지하철',   '🚇', 'E'),
       (@U, @C_TRANS,   '택시',     '🚕', 'E'),
       (@U, @C_HOME,    '월세',     '🔑', 'E'),
       (@U, @C_HOME,    '관리비',   '🧾', 'E'),
       (@U, @C_HOME,    '통신비',   '📱', 'E'),
       (@U, @C_CULTURE, '영화',     '🎞', 'E'),
       (@U, @C_CULTURE, '구독',     '📺', 'E'),
       (@U, @C_MED,     '약국',     '💉', 'E'),
       (@U, @C_PAY,     '월급',     '🏦', 'I'),
       (@U, @C_GIFT,    '부모님',   '👪', 'I');

-- ── 2) 거래 ──────────────────────────────────────────────────
-- 숫자 생성기 (0 ~ 4095). 재귀 CTE 없이 조인으로 만든다.
DROP TEMPORARY TABLE IF EXISTS seq;
CREATE TEMPORARY TABLE seq (n INT);
INSERT INTO seq (n)
SELECT a.i + b.i*4 + c.i*16 + d.i*64 + e.i*256 + f.i*1024
FROM (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) a,
     (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) b,
     (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) c,
     (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) d,
     (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) e,
     (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) f;

-- 지출 거래: 최근 180일에 걸쳐 3,800건
-- 카테고리는 소분류 위주로 뽑되, 5% 는 대분류에 직접 단다
INSERT INTO personal_transactions
    (user_num, category_num, trans_type, trans_amount, trans_date, trans_memo)
SELECT
    @U,
    c.category_num,
    'E',
    CASE
        WHEN c.category_name = '월세'   THEN 550000
        WHEN c.category_name = '관리비' THEN 70000  + FLOOR(RAND()*30000)
        WHEN c.category_name = '통신비' THEN 39000
        WHEN c.category_name = '구독'   THEN 13900
        ELSE 1000 + FLOOR(RAND()*40000)
    END,
    DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*180) DAY),
    CONCAT(c.category_name, ' 지출')
FROM seq s
JOIN (
    SELECT category_num, category_name,
           ROW_NUMBER() OVER (ORDER BY category_num) AS rn,
           COUNT(*)     OVER ()                      AS cnt
    FROM personal_categories
    WHERE user_num = @U AND category_type = 'E' AND is_default_yn = 'N'
) c ON c.rn = (s.n % c.cnt) + 1
WHERE s.n < 3800;

-- 수입 거래: 6개월치 월급 + 가끔 용돈
INSERT INTO personal_transactions
    (user_num, category_num, trans_type, trans_amount, trans_date, trans_memo)
SELECT @U,
       (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='월급'),
       'I', 3200000,
       DATE_SUB(CURDATE(), INTERVAL s.n*30 DAY),
       '월급'
FROM seq s WHERE s.n < 6;

INSERT INTO personal_transactions
    (user_num, category_num, trans_type, trans_amount, trans_date, trans_memo)
SELECT @U,
       (SELECT category_num FROM personal_categories WHERE user_num=@U AND category_name='부모님'),
       'I', 100000 + FLOOR(RAND()*200000),
       DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*180) DAY),
       '용돈'
FROM seq s WHERE s.n < 12;

-- 대분류에 직접 단 거래 (소분류뷰에서 '기타'로 보일 것들)
INSERT INTO personal_transactions
    (user_num, category_num, trans_type, trans_amount, trans_date, trans_memo)
SELECT @U, @C_FOOD, 'E', 3000 + FLOOR(RAND()*12000),
       DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND()*180) DAY), '급해서 대분류로'
FROM seq s WHERE s.n < 60;

DROP TEMPORARY TABLE seq;

-- ── 3) 결과 ──────────────────────────────────────────────────
SELECT '── 카테고리 ──' AS '';
SELECT COUNT(*) AS 카테고리수 FROM personal_categories WHERE user_num=@U;
SELECT '── 거래 ──' AS '';
SELECT COUNT(*) AS 거래수, MIN(trans_date) AS 시작일, MAX(trans_date) AS 종료일
FROM personal_transactions WHERE user_num=@U;
SELECT '── 월별 ──' AS '';
SELECT DATE_FORMAT(trans_date,'%Y-%m') AS 월, trans_type AS 타입,
       COUNT(*) AS 건수, SUM(trans_amount) AS 합계
FROM personal_transactions WHERE user_num=@U
GROUP BY 월, 타입 ORDER BY 월, 타입;
