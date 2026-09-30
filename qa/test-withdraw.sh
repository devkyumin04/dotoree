#!/bin/bash
# 회원 탈퇴 — 30일 유예 → 파기  (POST /api/users/withdraw, ADR-052)
# 사용법: bash qa/test-withdraw.sh
# 전제: 서버 기동(최신 코드로 재시작), export QA_PASSWORD='비밀번호'
#       test@test.com · rollback@test.com 은 쓰지 않는다 — 탈퇴시키면 다른 스크립트가 깨진다. 매번 새 계정을 만든다
#
# 검사하는 것
#   · 입구 — 무토큰 401 / 비번 누락 400 / 틀린 비번 400 (401 이 아니다 — 401 이면 apiClient.js 가 로그아웃시킨다)
#   · 탈퇴 204 → DB 'W' + 유예 시작 시각 / 남은 토큰으로 또 탈퇴 401 / 유예 중 같은 이메일 재가입 409
#   · 유예 중 로그인 — 틀린 비번이면 401 이고 'W' 그대로(비번 확인이 복구보다 앞) / 맞으면 200 = 탈퇴 취소, 가계부 그대로
#   · 파기 — 유예 시작을 31일 전으로 돌려 놓고 스케줄러를 기다린다: 계정·카테고리·거래 0행(FK 순서),
#            유예 중인 다른 계정은 남음, 파기 후 로그인 401, 같은 이메일로 재가입 201
#     스케줄러가 몇 초마다 돌 때만 (앱에 DOTOREE_PURGE_CRON + 여기에 QA_PURGE_FAST=1 — CI 가 켠다).
#     로컬 기본값(매일 03:30)이면 이 묶음은 SKIP
#
# DB 를 직접 보는 곳이 있다 — 로컬에서 QA_DB_PASSWORD 가 없으면 mysql 비번을 몇 번 묻는다

. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"
ask_reset

W=/api/users/withdraw

# eq · dbv 는 _lib.sh (2026-09-30 이동)

# ── 준비: 새 계정 둘 (E1 = 주인공, E2 = 유예 중으로 남겨 둘 대조군)
STAMP=$(date +%s)
E1="qa-w1-$STAMP@test.com"; E2="qa-w2-$STAMP@test.com"
B="{\"email\":\"$E1\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"탈퇴QA\"}"
LAST=$(raw POST /api/users/signup "$B"); U1=$(jnum userNum); neednum "$U1" "가입 $E1"
B="{\"email\":\"$E2\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"탈퇴QA2\"}"
LAST=$(raw POST /api/users/signup "$B"); U2=$(jnum userNum); neednum "$U2" "가입 $E2"

TOKEN=$(login "$E1"); checktoken "$TOKEN" "$E1"

# 파기 확인용 가계부 — 대분류 + 소분류 + 거래 (삭제 순서가 FK 와 다르면 1451 로 파기가 실패한다)
B='{"categoryName":"QA탈퇴대분류","categoryType":"E"}'
LAST=$(req POST /api/categories "$B"); CP=$(jnum categoryNum); neednum "$CP" 대분류
B="{\"categoryName\":\"QA탈퇴소분류\",\"categoryType\":\"E\",\"parentCategoryNum\":$CP}"
LAST=$(req POST /api/categories "$B"); CC=$(jnum categoryNum); neednum "$CC" 소분류
TODAY=$(date +%F)
B="{\"categoryNum\":$CC,\"transAmount\":1000,\"transDate\":\"$TODAY\"}"
LAST=$(req POST /api/transactions "$B"); neednum "$(jnum transNum)" 거래

OK_PW="{\"password\":\"$QA_PASSWORD\"}"

echo "── 1. 입구"
traw 1-a 401 POST $W "$OK_PW"
B='{}'
t    1-b 400 POST $W "$B"
B='{"password":"wrong-password-1"}'
t    1-c 400 POST $W "$B"
has  1-d "비밀번호가 일치하지 않습니다"
eq   1-e "A" "$(dbv "SELECT user_status FROM users WHERE user_num = $U1")"

echo "── 2. 탈퇴"
t    2-a 204 POST $W "$OK_PW"
eq   2-b "W 1" "$(dbv "SELECT user_status, user_withdrawn_at IS NOT NULL FROM users WHERE user_num = $U1")"
t    2-c 401 POST $W "$OK_PW"   # 남은 토큰으로 또 — 이미 'W' 라 0행 → 없는 사용자
B="{\"email\":\"$E1\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"재가입\"}"
traw 2-d 409 POST /api/users/signup "$B"   # 유예 중엔 이메일이 묶여 있다

echo "── 3. 유예 중 로그인 = 탈퇴 취소"
B="{\"email\":\"$E1\",\"password\":\"wrong-password-1\"}"
traw 3-a 401 POST /api/users/login "$B"
eq   3-b "W" "$(dbv "SELECT user_status FROM users WHERE user_num = $U1")"   # 틀린 비번으로는 복구 안 됨
B="{\"email\":\"$E1\",\"password\":\"$QA_PASSWORD\"}"
traw 3-c 200 POST /api/users/login "$B"
eq   3-d "A 1" "$(dbv "SELECT user_status, user_withdrawn_at IS NULL FROM users WHERE user_num = $U1")"
TOKEN=$(login "$E1"); checktoken "$TOKEN" "$E1 복구 후"
t    3-e 200 GET "/api/transactions?year=$(date +%Y)&month=$(date +%-m)"
has  3-f "QA탈퇴소분류"   # 가계부가 그대로 살아 있다

echo "── 4. 파기"
t    4-a 204 POST $W "$OK_PW"
TOKEN=$(login "$E2"); checktoken "$TOKEN" "$E2"
t    4-b 204 POST $W "$OK_PW"
if [ "$QA_PURGE_FAST" = "1" ]; then
  # E1 만 유예를 31일 전으로 — E2 는 방금 탈퇴한 그대로(대조군)
  dbv "UPDATE users SET user_withdrawn_at = NOW() - INTERVAL 31 DAY WHERE user_num = $U1" > /dev/null
  for i in $(seq 1 20); do
    [ "$(dbv "SELECT COUNT(*) FROM users WHERE user_num = $U1")" = "0" ] && break
    sleep 1
  done
  eq   4-c "0 0 0" "$(dbv "SELECT (SELECT COUNT(*) FROM users WHERE user_num = $U1), (SELECT COUNT(*) FROM personal_categories WHERE user_num = $U1), (SELECT COUNT(*) FROM personal_transactions WHERE user_num = $U1)")"
  eq   4-d "W" "$(dbv "SELECT user_status FROM users WHERE user_num = $U2")"   # 유예 중인 계정은 남는다
  B="{\"email\":\"$E1\",\"password\":\"$QA_PASSWORD\"}"
  traw 4-e 401 POST /api/users/login "$B"
  B="{\"email\":\"$E1\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"재가입\"}"
  traw 4-f 201 POST /api/users/signup "$B"   # 파기되면 이메일이 풀린다
else
  echo "⏭  4-c ~ 4-f SKIP — 파기 스케줄러가 매일 03:30 이라 기다릴 수 없다. CI(QA_PURGE_FAST=1)에서 확인"
fi

# 공용 테스트 계정은 건드리지 않았다
eq   5 "A A" "$(dbv "SELECT user_status FROM users WHERE user_email IN ('test@test.com', 'rollback@test.com') ORDER BY user_email")"

summary
