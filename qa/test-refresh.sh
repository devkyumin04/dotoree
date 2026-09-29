#!/bin/bash
# Refresh 토큰 — 발급 · 재발급(로테이션) · 유예 · 재사용 탐지 · 로그아웃 · 전 세션 폐기  (POST /api/auth/refresh·logout, ADR-055)
# 사용법: bash qa/test-refresh.sh
# 전제: 서버 기동(최신 코드), export QA_PASSWORD='비밀번호'
#       전용 계정을 매번 새로 만든다 — 비번 변경·탈퇴를 하므로 공용 계정을 쓰면 중간에 실패할 때 다른 스크립트가 깨진다
#       DB 를 직접 보는 곳이 있다(2·3번) — 로컬에서 QA_DB_PASSWORD 가 없으면 mysql 비번을 몇 번 묻는다
#
# 기기 = 쿠키 파일(jar) 하나. curl -b 로 카드를 싣고 -c 로 응답의 새 쿠키를 같은 파일에 받는다(브라우저와 같은 동작)
# 카드 원문은 화면에 찍지 않는다 — 비교만 한다
#
# 검사하는 것
#   1. 로그인 200 + 쿠키 속성(HttpOnly·Secure·SameSite=Strict·Path=/api/auth·14일) / 재발급 200 + 새 카드 + 새 Access / 쿠키 없이 401
#   2. 교체된 옛 카드 30초 안 → 200(유예) + 살아 있는 카드는 하나
#   3. 30초 지난 옛 카드 → 401(탐지) → 유예로 받은 최신 카드도 401(일행 회수 · 부활 차단 · noRollbackFor)
#      30초는 기다리지 않고 DB 에서 revoked_at 을 31초 당긴다(test-withdraw 4번의 유예 31일과 같은 방식). 상대값이라 JVM·MySQL 시계 차이와 무관
#   4. 도둑 장면 — 복사본으로 먼저 재발급 → 원래 카드로 로그아웃 204 + 지우는 쿠키 → 도둑 카드 401 / 쿠키 없이 로그아웃 204
#   5. 비번 변경 → 두 기기 모두 401 → 새 비번 로그인 200
#   6. 탈퇴 → 두 기기 모두 401 → 로그인(= 탈퇴 취소, ADR-052) → 재발급 200

. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"
ask_reset

J=$(mktemp -d)                 # 쿠키 파일·응답 임시 폴더 — 끝나면(실패해도) 지운다
trap 'rm -rf "$J"' EXIT
RT=/api/auth/refresh; LO=/api/auth/logout; LOGIN=/api/users/login

# ── 이 스크립트 전용 헬퍼 ─────────────────────────────────────
# call : 요청 + 상태 코드 판정. 본문은 $J/b, 헤더는 $J/h, 쿠키는 jar 에서 싣고 jar 로 받는다
call() { # label expected jar method path [body]
  local code
  if [ -n "$6" ]; then
    code=$(curl -s -o "$J/b" -D "$J/h" -w "%{http_code}" -b "$3" -c "$3" -X "$4" "$BASE$5" -H "Content-Type: application/json" -d "$6")
  else
    code=$(curl -s -o "$J/b" -D "$J/h" -w "%{http_code}" -b "$3" -c "$3" -X "$4" "$BASE$5")
  fi
  if [ "$code" = "$2" ]; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$2"
  else
    FAIL=$((FAIL+1))
    case "$code" in 2*) printf "❌ %-6s expected %s got %s\n" "$1" "$2" "$code" ;;              # 2xx 본문엔 Access 가 있다 — 찍지 않는다
                    *)  printf "❌ %-6s expected %s got %s  → %s\n" "$1" "$2" "$code" "$(cat "$J/b")" ;; esac
  fi
}
# setc : 직전 응답의 refresh_token Set-Cookie 줄에 기대 조각이 전부 있는가
setc() { # label 조각...
  local label="$1"; shift
  local line=$(grep -i '^set-cookie: refresh_token=' "$J/h" | tr -d '\r')
  local miss="" p
  for p in "$@"; do echo "$line" | grep -q -- "$p" || miss="$miss $p"; done
  if [ -n "$line" ] && [ -z "$miss" ]; then PASS=$((PASS+1)); printf "✅ %-6s Set-Cookie %s\n" "$label" "$*"
  else FAIL=$((FAIL+1)); printf "❌ %-6s Set-Cookie 없음 또는 빠짐:%s\n" "$label" "${miss:- (줄 없음)}"; fi
}
ck() { awk '$6 == "refresh_token" { print $7 }' "$1" 2>/dev/null; }   # jar 안 카드 원문 (없으면 빈 값)
needck() { # jar 설명 — 카드가 실제로 jar 에 들어왔는가. 없으면 뒤의 401 은 "쿠키 없음 401" 이라 증거가 아니다
  [ -n "$(ck "$1")" ] || { echo "❌ 기준 데이터 실패 ($2): jar 에 refresh_token 없음"; exit 1; }
}
access() { sed 's/.*"accessToken":"\([^"]*\)".*/\1/' "$J/b"; }         # 직전 응답 본문의 Access (t() 가 쓰는 TOKEN)
# eq · dbv — test-withdraw.sh 와 같은 것. 세 번째 복제가 생기면 _lib.sh 로 (ADR-041)
eq() { # label 기대값 실제값
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$2"
  else FAIL=$((FAIL+1)); printf "❌ %-6s expected [%s] got [%s]\n" "$1" "$2" "$3"; fi
}
dbv() { qa_mysql -N -e "$1" | tr '\t\n' '  ' | sed 's/ *$//'; }

# ── 준비: 전용 계정
E="qa-rt-$(date +%s)@test.com"
B="{\"email\":\"$E\",\"password\":\"$QA_PASSWORD\",\"nickname\":\"리프레시QA\"}"
LAST=$(raw POST /api/users/signup "$B"); U=$(jnum userNum); neednum "$U" "가입 $E"
PW_OK="{\"email\":\"$E\",\"password\":\"$QA_PASSWORD\"}"
ALIVE="SELECT COUNT(*) FROM refresh_tokens WHERE user_num = $U AND revoked_yn = 'N'"

echo "── 1. 발급 · 재발급(로테이션)"
call 1-a 200 "$J/a" POST $LOGIN "$PW_OK"
setc 1-b "HttpOnly" "Secure" "SameSite=Strict" "Path=/api/auth" "Max-Age=1209600"
needck "$J/a" "로그인"
cp "$J/a" "$J/a-old"                             # 교체 전 카드(A) 보관 — 2·3번의 옛 카드
call 1-c 200 "$J/a" POST $RT
setc 1-d "HttpOnly" "Secure" "SameSite=Strict" "Path=/api/auth" "Max-Age=1209600"
eq   1-e "새 카드" "$( [ -n "$(ck "$J/a")" ] && [ "$(ck "$J/a")" != "$(ck "$J/a-old")" ] && echo 새 카드 || echo 그대로)"
eq   1-f "Access" "$(grep -q '"accessToken":"[^"]' "$J/b" && echo Access || echo 없음)"
call 1-g 401 "$J/none" POST $RT                  # 쿠키 없이

echo "── 2. 유예 (교체된 옛 카드가 30초 안에 다시 옴 = 응답 유실 뒤 재시도)"
cp "$J/a-old" "$J/retry"
call 2-a 200 "$J/retry" POST $RT
eq   2-b "1" "$(dbv "$ALIVE")"                   # 유예로 새 카드를 줘도 살아 있는 건 하나 (ADR-055 선택 10)

echo "── 3. 재사용 탐지 (30초 지난 옛 카드)"
dbv "UPDATE refresh_tokens SET revoked_at = revoked_at - INTERVAL 31 SECOND WHERE user_num = $U AND revoked_yn = 'Y'" > /dev/null
call 3-a 401 "$J/a-old" POST $RT                 # 탐지 → 일행 회수
call 3-b 401 "$J/retry" POST $RT                 # 유예로 받은 최신 카드도 끝 — 200 이면 회수가 롤백된 것(noRollbackFor) 또는 부활
eq   3-c "0" "$(dbv "$ALIVE")"

echo "── 4. 로그아웃 — 도둑 장면"
call 4-a 200 "$J/owner" POST $LOGIN "$PW_OK"; needck "$J/owner" "4 로그인"
cp "$J/owner" "$J/thief"                         # 도둑이 카드를 복사해 간다
call 4-b 200 "$J/thief" POST $RT                 # 도둑이 먼저 로테이션 — 주인 카드는 이미 폐기
call 4-c 204 "$J/owner" POST $LO                 # 주인이 로그아웃 — 폐기된 카드여도 일행 회수
setc 4-d "Max-Age=0;" "Path=/api/auth"
eq   4-e "지워짐" "$( [ -z "$(ck "$J/owner")" ] && echo 지워짐 || echo 남음)"   # 이름·경로가 같아야 덮어써서 지워진다
call 4-f 401 "$J/thief" POST $RT
call 4-g 204 "$J/none" POST $LO                  # 쿠키 없이도 204 (멱등)

echo "── 5. 비밀번호 변경 → 모든 기기"
NEW='Qa-rt-pass-1!'
call 5-a 200 "$J/d1" POST $LOGIN "$PW_OK"; needck "$J/d1" "기기1"
TOKEN=$(access); checktoken "$TOKEN" "기기1"
call 5-b 200 "$J/d2" POST $LOGIN "$PW_OK"; needck "$J/d2" "기기2"
B="{\"currentPassword\":\"$QA_PASSWORD\",\"newPassword\":\"$NEW\"}"
t    5-c 204 PUT /api/users/me/password "$B"
call 5-d 401 "$J/d1" POST $RT                    # 바꾼 이 기기도 (ADR-055 선택 16)
call 5-e 401 "$J/d2" POST $RT
PW_NEW="{\"email\":\"$E\",\"password\":\"$NEW\"}"
call 5-f 200 "$J/d1" POST $LOGIN "$PW_NEW"

echo "── 6. 탈퇴 → 모든 기기"
TOKEN=$(access); checktoken "$TOKEN" "새 비번 로그인"
call 6-a 200 "$J/d2" POST $LOGIN "$PW_NEW"
B="{\"password\":\"$NEW\"}"
t    6-b 204 POST /api/users/withdraw "$B"
call 6-c 401 "$J/d1" POST $RT                    # 유예 중('W') 계정이 옛 카드로 재발급받던 구멍
call 6-d 401 "$J/d2" POST $RT
call 6-e 200 "$J/d1" POST $LOGIN "$PW_NEW"       # 로그인 = 탈퇴 취소
call 6-f 200 "$J/d1" POST $RT

summary
