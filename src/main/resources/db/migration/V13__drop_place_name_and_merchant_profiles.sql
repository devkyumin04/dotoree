-- 안 쓰는 칸 · 표 정리 ③ (ADR-070) — 기획에서 뺀 지도 · 임베딩의 자리
--
--   personal_transactions.place_name   장소명 — 지도(카카오맵 장소 검색)를 뺐다. 좌표 칸은 V11 에서 이미 지웠다
--   merchant_profiles                   상호명 임베딩 — 임베딩(옛 Sprint 3)을 뺐다(2026-11-02). 되살리면 그때 새로 만든다
--
-- 어느 매퍼 SQL 도 이 칸 · 표를 부르지 않는다(INSERT · SELECT 0곳) → 이 배포의 옛 jar(.prev)로 롤백해도 돈다.
-- 그래서 ADR-053 의 "두 번에 나눠 지우기" 없이 한 번에. 되돌릴 수 없다(DROP) — 값이 없는 칸 · 빈 표이고, 매일 04:00 백업이 있다

ALTER TABLE personal_transactions
    DROP COLUMN place_name;

DROP TABLE merchant_profiles;
