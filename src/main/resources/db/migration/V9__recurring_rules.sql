-- 반복 거래 · 이체 규칙과 처리한 예정 (욕조 ② — ADR-067)
--
-- recurring_rules        "매달 25일 월세 500,000 · 국민통장" 같은 규칙. 예정은 저장하지 않고 규칙에서 계산한다(잔액처럼 — 진실은 규칙 하나)
--   거래 규칙  = category_num + account_num (수입·지출은 카테고리에서 — 거래와 같은 규칙, ADR-008)
--   이체 규칙  = from_account_num + to_account_num (카드값 · 적금)
--   repeat_type   M 매달(repeat_day 일, 그 달에 없으면 말일) · Y 매년(repeat_month 월 repeat_day 일, 2/29 는 평년 2/28)
--   start_date    첫 예정을 찾기 시작하는 날(오늘 이후). end_date 가 있으면 그날까지(할부)
-- recurring_occurrences  사용자가 처리한 예정 — C 확인(거래 · 이체가 생김) / S 건너뜀. 남은 예정 = 계산한 예정 − 여기 있는 것
--   (rule_num, scheduled_date) UNIQUE — 같은 예정을 두 번 확인하는 걸 DB 가 막는다(두 번 눌러도 거래가 두 줄이 안 된다)
--
-- forward-only (ADR-053) — 새 테이블 둘이라 옛 jar 는 모른다. 옛 jar 로 롤백하면 그 jar 의 파기가 이 FK 에 걸린다(ADR-065 감수와 같음)
CREATE TABLE recurring_rules (
    rule_num          INT AUTO_INCREMENT PRIMARY KEY,
    user_num          INT NOT NULL,
    category_num      INT NULL,
    account_num       INT NULL,
    from_account_num  INT NULL,
    to_account_num    INT NULL,
    rule_amount       BIGINT NOT NULL,
    rule_memo         VARCHAR(100),
    repeat_type       CHAR(1) NOT NULL,
    repeat_month      TINYINT NULL,
    repeat_day        TINYINT NOT NULL,
    start_date        DATE NOT NULL,
    end_date          DATE NULL,
    use_yn            CHAR(1) NOT NULL DEFAULT 'Y',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_user FOREIGN KEY (user_num) REFERENCES users(user_num),
    CONSTRAINT fk_rule_category FOREIGN KEY (category_num) REFERENCES personal_categories(category_num),
    CONSTRAINT fk_rule_account FOREIGN KEY (account_num) REFERENCES accounts(account_num),
    CONSTRAINT fk_rule_from FOREIGN KEY (from_account_num) REFERENCES accounts(account_num),
    CONSTRAINT fk_rule_to FOREIGN KEY (to_account_num) REFERENCES accounts(account_num),
    CONSTRAINT chk_rule_amount CHECK (rule_amount > 0),
    CONSTRAINT chk_rule_repeat CHECK (
        (repeat_type = 'M' AND repeat_month IS NULL)
        OR (repeat_type = 'Y' AND repeat_month BETWEEN 1 AND 12)),
    CONSTRAINT chk_rule_day CHECK (repeat_day BETWEEN 1 AND 31),
    CONSTRAINT chk_rule_target CHECK (
        (category_num IS NOT NULL AND account_num IS NOT NULL AND from_account_num IS NULL AND to_account_num IS NULL)
        OR (category_num IS NULL AND account_num IS NULL AND from_account_num IS NOT NULL AND to_account_num IS NOT NULL
            AND from_account_num <> to_account_num)),
    CONSTRAINT chk_rule_dates CHECK (end_date IS NULL OR end_date >= start_date),
    KEY idx_rule_user (user_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recurring_occurrences (
    occurrence_num     INT AUTO_INCREMENT PRIMARY KEY,
    rule_num           INT NOT NULL,
    user_num           INT NOT NULL,
    scheduled_date     DATE NOT NULL,
    occurrence_status  CHAR(1) NOT NULL,
    trans_num          INT NULL,
    transfer_num       INT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_occ_rule FOREIGN KEY (rule_num) REFERENCES recurring_rules(rule_num),
    CONSTRAINT fk_occ_user FOREIGN KEY (user_num) REFERENCES users(user_num),
    CONSTRAINT fk_occ_trans FOREIGN KEY (trans_num) REFERENCES personal_transactions(trans_num),
    CONSTRAINT fk_occ_transfer FOREIGN KEY (transfer_num) REFERENCES transfers(transfer_num),
    CONSTRAINT chk_occ_status CHECK (occurrence_status IN ('C', 'S')),
    UNIQUE KEY uk_occurrence (rule_num, scheduled_date),
    KEY idx_occ_user (user_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
