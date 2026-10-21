-- 계좌의 시작 날짜 (ADR-065 ⑦)
--
-- opening_date  시작 잔액이 "며칠 기준 잔액"인지. 잔액에는 이 날짜 이후 거래 · 이체만 더한다
--               — 지난 거래를 나중에 채워 넣어도, 이미 시작 잔액에 들어 있는 돈을 두 번 세지 않는다(통계엔 그대로 잡힌다)
--               NULL = 시작 날짜 없음(처음부터 모든 거래를 더한다) — 가입 때 자동으로 만든 '현금' 기본 계좌.
--               아무도 "그날 현금이 0원"이라고 말한 적이 없어서, 가입 전 날짜의 현금 거래도 잔액에 들어가야 한다
--
-- forward-only (ADR-053) — NULL 허용이라 롤백한 옛 jar 의 INSERT 는 NULL(= 지금까지와 같은 계산)이 된다
-- 기존 계좌 — 기본 계좌는 NULL 그대로, 직접 만든 계좌는 만든 날
ALTER TABLE accounts
    ADD COLUMN opening_date DATE NULL AFTER credit_limit;

UPDATE accounts
SET opening_date = DATE(created_at)
WHERE is_default_yn = 'N';
