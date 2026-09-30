#!/bin/bash
# 로그인 잠금 — 계정 기준 5회 실패 → 30분 잠금 · 잠금 중 거절 · 만료 뒤 0 부터 · 성공 시 초기화  (POST /api/users/login, ADR-057)
# 사용법: bash qa/test-login-lock.sh
# 전제: 서버 기동(최신 코드), export QA_PASSWORD='비밀번호'
#       전용 계정을 매번 새로 만든다 — 공용 계정을 잠그면 뒤에 도는 스크립트가 줄줄이 401·423 이 된다
#       DB 를 직접 본다(횟수·잠금 시각, 30분 시간 여행) — 로컬에서 QA_DB_PASSWORD 가 없으면 mysql 비번을 몇 번 묻는다
#
# 검사하는 것
#   1. 틀린 비번 4회 → 401 넷, 횟수 4, 잠금 없음
#   2. 5회째 → 423 + "잠겼습니다" + "30분", 횟수 5, 잠금 시각 있음
#   3. 잠금 중 — 맞는 비번도 423 / 틀린 비번 423 인데 횟수·잠금 시각 그대로(세지 않음, 연장 없음) / 기존 카드 재발급은 200(잠금은 비번 로그인만 막는다)
#   4. 30분 뒤(DB 에서 locked_until 을 31분 당김) — 틀린 비번 1회 → 401 이고 횟수 1(6 이 아니다) / 맞는 비번 200 → 0·NULL
#   5. 없는 이메일 5회 → 전부 401, 문구 동일 (셀 계정이 없다)
#   6. 성공 시 초기화 — 4회 틀리고 맞으면 0 → 다시 1회 틀려도 401(잠기지 않는다)
#   7. 정리 — 탈퇴 204 (파기 스케줄러가 지운다)

. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"
ask_reset

J=$(mktemp -d)                 # 쿠키 파일 임시 폴더 — 끝나면(실패해도) 지운다
trap 'rm -rf "$J"' EXIT
LOGIN=/api/users/login; RT=/api/auth/refresh

# ── 준비: 전용 계정
E="qa-lock-$(date +%s)@test.com"
B="{\"email\":\"$E\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"잠금QA\"}"
LAST=$(raw POST /api/users/signup "$B"); U=$(jnum userNum); neednum "$U" "가입 $E"
PW_OK="{\"email\":\"$E\",\"password\":\"$QA_PASSWORD\"}"
PW_BAD="{\"email\":\"$E\",\"password\":\"wrong-${QA_PASSWORD}\"}"
STATE="SELECT login_fail_count, IF(locked_until IS NULL, 'NULL', 'SET') FROM users WHERE user_num = $U"   # "횟수 잠금여부" 한 줄

echo "── 1. 잠기기 전 — 틀린 비번 4회"
# 첫 로그인은 쿠키 jar 로(3-c 에서 이 카드로 재발급) — 원문은 찍지 않는다
code=$(curl -s -o /dev/null -c "$J/card" -w "%{http_code}" -X POST "$BASE$LOGIN" -H "Content-Type: application/json" -d "$PW_OK")
eq   1-a 200 "$code"
for i in 1 2 3 4; do traw "1-b$i" 401 POST $LOGIN "$PW_BAD"; done
eq   1-c "4 NULL" "$(dbv "$STATE")"

echo "── 2. 5회째 → 잠김"
traw 2-a 423 POST $LOGIN "$PW_BAD"
has  2-b "잠겼습니다"
has  2-c "30분"
eq   2-d "5 SET" "$(dbv "$STATE")"
LOCK1=$(dbv "SELECT locked_until FROM users WHERE user_num = $U")

echo "── 3. 잠금 중"
traw 3-a 423 POST $LOGIN "$PW_OK"                # 맞는 비번도 거절 — 시도 자체를 막는다
traw 3-b 423 POST $LOGIN "$PW_BAD"
eq   3-c "5 SET" "$(dbv "$STATE")"                # 세지 않는다
eq   3-d "$LOCK1" "$(dbv "SELECT locked_until FROM users WHERE user_num = $U")"   # 연장하지 않는다
code=$(curl -s -o /dev/null -b "$J/card" -c "$J/card" -w "%{http_code}" -X POST "$BASE$RT")
eq   3-e 200 "$code"                              # 잠금은 비번 로그인만 막는다 — 이미 로그인된 기기는 그대로

echo "── 4. 30분 뒤 (DB 시간 여행 — 저장된 값 기준 -31분, 시계 차이와 무관)"
dbv "UPDATE users SET locked_until = locked_until - INTERVAL 31 MINUTE WHERE user_num = $U" > /dev/null
traw 4-a 401 POST $LOGIN "$PW_BAD"                # 만료 뒤 첫 실패 = 1회째 (6회째로 이어 세면 바로 다시 잠긴다)
eq   4-b "1 NULL" "$(dbv "$STATE")"
traw 4-c 200 POST $LOGIN "$PW_OK"
eq   4-d "0 NULL" "$(dbv "$STATE")"

echo "── 5. 없는 이메일 — 세지 않는다"
NOBODY="{\"email\":\"nobody-$(date +%s)@test.com\",\"password\":\"$QA_PASSWORD\"}"
for i in 1 2 3 4 5; do traw "5-$i" 401 POST $LOGIN "$NOBODY"; done
has  5-6 "일치하지 않습니다"                        # 잠김 문구가 아니다

echo "── 6. 성공 시 초기화"
for i in 1 2 3 4; do traw "6-a$i" 401 POST $LOGIN "$PW_BAD"; done
traw 6-b 200 POST $LOGIN "$PW_OK"
eq   6-c "0 NULL" "$(dbv "$STATE")"
traw 6-d 401 POST $LOGIN "$PW_BAD"                # 5회째가 아니라 1회째 — 423 이면 초기화가 안 된 것
eq   6-e "1 NULL" "$(dbv "$STATE")"

echo "── 7. 정리 — 탈퇴"
LAST=$(raw POST $LOGIN "$PW_OK"); TOKEN=$(echo "$LAST" | sed '$d' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/'); checktoken "$TOKEN" "정리 로그인"
B="{\"password\":\"$QA_PASSWORD\"}"
t    7-a 204 POST /api/users/withdraw "$B"

summary
