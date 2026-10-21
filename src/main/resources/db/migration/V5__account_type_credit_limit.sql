-- 계좌 종류에 마이너스통장(M) · 한도 칸 (ADR-065)
--
-- account_type  C 현금 / B 통장 / M 마이너스통장 / K 카드
-- credit_limit  한도(양수). 마이너스통장 · 카드만 — 시작 잔액은 −한도까지. NULL = 한도를 안 적음(제한 없음)
--               거래 · 이체로 한도를 넘는 건 막지 않는다(일어난 일은 기록) — 화면이 경고
--
-- forward-only (ADR-053) — 칸은 NULL 허용, CHECK 는 넓히기만. 롤백한 옛 jar 는 M 을 만들지 않고 이 칸을 모른다
ALTER TABLE accounts
    DROP CHECK chk_account_type;

ALTER TABLE accounts
    ADD CONSTRAINT chk_account_type CHECK (account_type IN ('C', 'B', 'M', 'K')),
    ADD COLUMN credit_limit BIGINT NULL AFTER opening_balance,
    ADD CONSTRAINT chk_account_credit_limit CHECK (credit_limit IS NULL OR credit_limit > 0);
