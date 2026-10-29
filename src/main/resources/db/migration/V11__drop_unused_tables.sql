-- 안 쓰는 표 · 칸 정리 ① (ADR-070) — 공동 가계부(10/24 기획에서 뺌) · 소셜 로그인(10/27 뺌) 표와, 아무 코드도 안 쓰는 칸
--
-- 지우는 것
--   · 공동 가계부 표 9개 + social_accounts — 코드에서 쓴 적 없다(옛 jar 도 마찬가지라 롤백해도 안전, ADR-053)
--   · receipt_analysis 의 공동 가계부용 칸(target_type · group_num · confirmed_gtrans_num)과 img_url(사진은 저장하지 않는다)
--   · personal_transactions 의 latitude · longitude · capture_img_url (지도 · 사진 저장 계획 없음)
-- 아직 안 지우는 것
--   · users 의 user_id · user_phone · user_birth · book_open_yn · signup_ip — 옛 jar 의 INSERT 가 이 칸들을 적는다. 이번 배포에서 코드가 안 쓰게 하고, 다음 배포(V12)에서 지운다 (ADR-053 의 두 배포)
-- 되돌릴 수 없다(DROP) — 모두 빈 표 · 빈 칸이고, 매일 04:00 백업이 있다

-- receipt_analysis → 공동 가계부 표를 가리키는 FK 부터 끊는다
ALTER TABLE receipt_analysis DROP FOREIGN KEY FK_ANALYSIS_GROUP;
ALTER TABLE receipt_analysis DROP FOREIGN KEY FK_ANALYSIS_GTRANS;
ALTER TABLE receipt_analysis
    DROP COLUMN target_type,
    DROP COLUMN group_num,
    DROP COLUMN confirmed_gtrans_num,
    DROP COLUMN img_url;

ALTER TABLE personal_transactions
    DROP COLUMN latitude,
    DROP COLUMN longitude,
    DROP COLUMN capture_img_url;

-- 공동 가계부 표 — 참조하는 쪽(자식)부터 지운다
DROP TABLE expense_logs;
DROP TABLE settlement_snapshots;
DROP TABLE group_transactions;
DROP TABLE group_merchant_profiles;
DROP TABLE invitations;
DROP TABLE group_members;
DROP TABLE group_periods;
DROP TABLE group_categories;
DROP TABLE `groups`;

DROP TABLE social_accounts;
