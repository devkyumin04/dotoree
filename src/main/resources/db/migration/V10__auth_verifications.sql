-- 이메일 인증 코드 (ADR-068)
--
-- 가입 인증 · 비밀번호 재설정에 보내는 6자리 코드 한 통 = 한 줄. 코드를 보낼 때는 아직 가입 전이라 users 에 사람이 없다 → user_num 대신 email 로 찾는다
--
-- purpose      S 가입 · P 비밀번호 재설정 — 가입용 코드로 비번을 바꾸지 못하게 목적을 같이 맞춘다
-- code_hash    코드 원문이 아니라 SHA-256(64글자). DB 를 열어 봐도 코드가 그대로 보이지 않게 (refresh_tokens.token_hash 와 같은 방식)
--              6자리라 DB 가 통째로 털리면 해시는 금방 풀린다 — 그때 막아 주는 건 10분 유효와 "한 번 쓰면 끝"이다
-- fail_count   틀린 횟수. 5 가 되면 그 코드는 버린다(새로 받아야). +1 은 가입 · 재설정 트랜잭션과 따로 커밋한다(ADR-062 와 같은 이유 — 롤백되면 영영 0)
-- used_at      가입 · 재설정에 쓴 시각. 쓴 코드는 다시 못 쓴다
-- created_at   보낸 시각 — 60초 간격 · 하루(24시간) 5번 · 10분 유효를 전부 이걸로 센다. 끝나는 시각 칸은 따로 두지 않는다(created_at + 10분).
--              시각은 자바가 넘긴다(ADR-060) — 판단에 쓰는 "지금"과 같은 시계여야 60초 · 10분이 맞는다
--
-- users 를 참조하지 않는다(FK 없음) — 가입 전 주소도 들어온다. 그래서 파기가 FK 에 걸리지는 않지만, 개인정보라 파기 때 같은 주소의 줄을 같이 지운다(UserMapper.deleteVerificationsOf).
-- 하루 지난 줄은 매일 새벽 정리한다(AuthCleanupScheduler) — 하루 5번을 세는 데 하루치만 있으면 된다
--
-- V1 에 같은 이름의 옛 설계(USER_NUM FK · 링크 토큰 VERIFY_TOKEN · EXPIRES_AT · USED_YN)가 빈 채로 있다 — 링크 방식 · 가입 후 인증을 전제로 미리 만든 것.
-- 코드 방식 · 가입 전 인증으로 바뀌어 모양이 맞지 않는다. 어떤 코드도 이 테이블에 쓴 적이 없어(매퍼 없음) 지우고 새로 만든다
-- forward-only (ADR-053) — 옛 jar 는 이 테이블을 쓰지 않아 롤백해도 부딪히지 않는다
DROP TABLE auth_verifications;

CREATE TABLE auth_verifications (
    verification_num  INT AUTO_INCREMENT PRIMARY KEY,
    email             VARCHAR(100) NOT NULL,
    purpose           CHAR(1) NOT NULL,
    code_hash         CHAR(64) NOT NULL,
    fail_count        INT NOT NULL DEFAULT 0,
    used_at           DATETIME NULL,
    created_at        DATETIME NOT NULL,
    CONSTRAINT chk_verify_purpose CHECK (purpose IN ('S', 'P')),
    KEY idx_verify_email_created (email, created_at),
    KEY idx_verify_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
