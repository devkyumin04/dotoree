-- 거래의 계좌 칸을 필수로 (ADR-065 ⑤ · ADR-053 의 두 번째 배포)
--
-- V4 는 롤백한 옛 jar(계좌 칸을 모르는 INSERT)를 위해 account_num 을 NULL 허용으로 열어 뒀다.
-- 그 뒤 jar 는 항상 채우고(비우면 서버가 기본 '현금'), V4 가 기존 거래를 기본 계좌로 옮겼다.
--
-- 남은 NULL 줄이 있으면 MODIFY 가 실패해 앱이 뜨지 않는다 → 먼저 그 사용자의 기본 계좌로 옮긴다(V4 와 같은 UPDATE)
-- 감수 — 이 뒤로는 V4 이전 jar 로 되돌리면 거래 등록이 실패한다
UPDATE personal_transactions t
JOIN accounts a ON a.user_num = t.user_num AND a.is_default_yn = 'Y'
SET t.account_num = a.account_num
WHERE t.account_num IS NULL;

ALTER TABLE personal_transactions
    MODIFY COLUMN account_num INT NOT NULL;
