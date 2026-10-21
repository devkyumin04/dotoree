-- 잔액 보정 (ADR-066)
--
-- 실제 은행 잔액과 앱 잔액이 다를 때(안 적은 이자 · 빼먹은 거래) 차이만큼 한 줄 — 실제 잔액을 넣으면 서버가 차이를 계산해 넣는다.
-- 거래 테이블과 따로 두는 이유는 이체와 같다(ADR-065 ①) — 번 돈도 쓴 돈도 아니라 통계에 안 들어가야 하는데,
-- 거래에 넣으면 통계 쿼리마다 "보정 빼기"가 붙는다. 따로 두면 통계는 그대로고 잔액 계산(balanceOf)에만 한 줄 더한다.
--
-- adjustment_amount  + 면 늘림, − 면 줄임. 0 은 만들지 않는다(차이가 없으면 서버가 아무것도 안 넣는다)
-- forward-only (ADR-053) — 새 테이블이라 옛 jar 는 모른다. 옛 jar 로 롤백하면 그 jar 의 파기가 이 테이블의 FK 에 걸려 실패한다
--                          (ADR-065 감수와 같음 — 로그 error, 다시 올리면 다음 날 지워진다)
CREATE TABLE balance_adjustments (
    adjustment_num     INT AUTO_INCREMENT PRIMARY KEY,
    user_num           INT NOT NULL,
    account_num        INT NOT NULL,
    adjustment_amount  BIGINT NOT NULL,
    adjustment_date    DATE NOT NULL,
    use_yn             CHAR(1) NOT NULL DEFAULT 'Y',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjust_user FOREIGN KEY (user_num) REFERENCES users(user_num),
    CONSTRAINT fk_adjust_account FOREIGN KEY (account_num) REFERENCES accounts(account_num),
    CONSTRAINT chk_adjust_amount CHECK (adjustment_amount <> 0),
    KEY idx_adjust_account_date (account_num, adjustment_date),
    KEY idx_adjust_user_date (user_num, adjustment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
