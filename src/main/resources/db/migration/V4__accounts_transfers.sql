-- 욕조 ① — 계좌 · 이체 · 거래에 계좌 칸 (ADR-065)
--
-- accounts         돈이 들어 있는 곳(현금 · 통장 · 카드). 잔액은 저장하지 않는다 — 시작 잔액 + 거래 · 이체로 볼 때마다 계산
--                  account_type  C 현금 / B 통장 / K 카드(잔액이 마이너스 = 갚을 돈)
--                  is_default_yn 가입 때 자동으로 만드는 '현금'. 못 지운다(거래가 갈 곳이 늘 하나는 있다)
--                  use_yn        N = 닫힘. 잔액 0 일 때만 닫는다 — 거래 · 이체 기록은 이 계좌에 그대로 남는다
-- transfers        계좌 사이에서 돈이 자리만 옮기는 것(통장 → 현금, 카드값 내기). 수입도 지출도 아니라 통계에 안 잡힌다
--                  거래 테이블과 따로 둔다 — 기존 거래 · 통계 쿼리를 안 건드리고, 보내는 계좌 ≠ 받는 계좌를 DB 제약으로
-- personal_transactions.account_num
--                  forward-only (ADR-053) — 이번엔 NULL 허용. 롤백한 옛 jar 의 INSERT 는 이 칸을 모른다
--                  새 jar 는 항상 채운다. NOT NULL 은 다음 배포에서(expand → contract)
--
-- 기존 데이터 — 사용자마다 기본 '현금' 계좌를 만들고 기존 거래를 그 계좌로. 거래가 계좌 없이 남지 않게

CREATE TABLE accounts (
    account_num      INT AUTO_INCREMENT PRIMARY KEY,
    user_num         INT NOT NULL,
    account_name     VARCHAR(20) NOT NULL,
    account_type     CHAR(1) NOT NULL,
    opening_balance  BIGINT NOT NULL DEFAULT 0,
    is_default_yn    CHAR(1) NOT NULL DEFAULT 'N',
    use_yn           CHAR(1) NOT NULL DEFAULT 'Y',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_user FOREIGN KEY (user_num) REFERENCES users(user_num),
    CONSTRAINT chk_account_type CHECK (account_type IN ('C', 'B', 'K')),
    KEY idx_account_user (user_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfers (
    transfer_num      INT AUTO_INCREMENT PRIMARY KEY,
    user_num          INT NOT NULL,
    from_account_num  INT NOT NULL,
    to_account_num    INT NOT NULL,
    transfer_amount   BIGINT NOT NULL,
    transfer_date     DATE NOT NULL,
    transfer_memo     VARCHAR(100),
    version           INT NOT NULL DEFAULT 0,
    use_yn            CHAR(1) NOT NULL DEFAULT 'Y',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_user FOREIGN KEY (user_num) REFERENCES users(user_num),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_num) REFERENCES accounts(account_num),
    CONSTRAINT fk_transfer_to   FOREIGN KEY (to_account_num) REFERENCES accounts(account_num),
    CONSTRAINT chk_transfer_amount CHECK (transfer_amount > 0),
    CONSTRAINT chk_transfer_accounts CHECK (from_account_num <> to_account_num),
    KEY idx_transfer_user_date (user_num, transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE personal_transactions
    ADD COLUMN account_num INT NULL AFTER category_num,
    ADD CONSTRAINT fk_ptrans_account FOREIGN KEY (account_num) REFERENCES accounts(account_num),
    ADD KEY idx_ptrans_account_date (account_num, trans_date);

INSERT INTO accounts (user_num, account_name, account_type, opening_balance, is_default_yn)
SELECT user_num, '현금', 'C', 0, 'Y'
FROM users;

UPDATE personal_transactions t
JOIN accounts a ON a.user_num = t.user_num AND a.is_default_yn = 'Y'
SET t.account_num = a.account_num;
