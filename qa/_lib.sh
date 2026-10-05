#!/bin/bash
# QA 스크립트 공통 헬퍼. 각 스크립트 상단에서 아래 한 줄로 불러온다.
#   . "$(cd "$(dirname "$0")" && pwd)/_lib.sh"
#
# 담고 있는 것: 서버 주소, 비밀번호 확인, PASS/FAIL 카운터,
#               로그인·요청·판정 헬퍼, 시작 시 데이터 초기화 확인
#
# 환경변수 (전부 선택 — 안 주면 로컬에서 쓰던 그대로 동작한다. CI 게이트가 덮어쓴다)
#   BASE            서버 주소. 기본 http://localhost:8080
#   QA_RESET        y/n 을 주면 초기화 여부를 묻지 않는다 (CI 엔 대답해 줄 사람이 없다)
#   QA_DB_HOST      있으면 mysql -h 로 TCP 접속. 러너의 MySQL 은 서비스 컨테이너라 소켓이 없다 (127.0.0.1)
#   QA_DB_USER      기본 root
#   QA_DB_PASSWORD  있으면 프롬프트 없이 접속. 없으면 -p 로 묻는다
#
# 주의: 중첩 명령치환 "$(req ... "body")" 은 macOS /bin/sh 에서 body 가 유실된다.
#       본문은 B=... 변수에 담고 t() 에 넘길 것. (트러블슈팅 8)

BASE="${BASE:-http://localhost:8080}"
QA_PASSWORD="${QA_PASSWORD:?QA_PASSWORD 환경변수가 필요합니다.  예) export QA_PASSWORD='비밀번호'}"
QA_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=0; FAIL=0; LAST=""

# ── DB 접속 ───────────────────────────────────────────────
# 접속 방법을 한 곳에 둔다 (reset-all.sh 도 이걸 쓴다). 비번은 명령줄 인자가 아니라
# MYSQL_PWD 로 넘긴다 — 인자로 주면 프로세스 목록에 보이고 mysql 이 경고를 찍는다.
qa_mysql() {
  if [ -n "$QA_DB_PASSWORD" ]; then
    MYSQL_PWD="$QA_DB_PASSWORD" mysql ${QA_DB_HOST:+-h "$QA_DB_HOST"} -u "${QA_DB_USER:-root}" dotoree "$@"
  else
    mysql ${QA_DB_HOST:+-h "$QA_DB_HOST"} -u "${QA_DB_USER:-root}" -p dotoree "$@"
  fi
}

# ── 시작 시 초기화 여부를 묻는다 ──────────────────────────────
# 항상 자동으로 밀면 화면 개발 중 손으로 넣은 데이터가 매번 날아가고,
# 반대로 안 밀면 QA 카테고리가 계속 쌓인다. 그래서 그때그때 고르게 한다.
# (유저와 '미분류'는 reset-data.sql 이 남기므로 재가입은 필요 없다)
ask_reset() {
  if [ -n "$QA_RESET" ]; then
    ANS="$QA_RESET"   # 비대화 모드 — 묻지 않는다
  else
    printf "기존 거래·카테고리를 지우고 시작할까요? (y/N) "
    read ANS
  fi
  case "$ANS" in
    [yY]*)
      qa_mysql < "$QA_DIR/reset-data.sql" || { echo "❌ 초기화 실패"; exit 1; }
      echo ;;
    *) echo "→ 기존 데이터 유지"; echo ;;
  esac
}

# ── 로그인 ────────────────────────────────────────────────
login() {
  curl -s -X POST $BASE/api/users/login -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$QA_PASSWORD\"}" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/'
}
checktoken() {
  local dots=$(echo "$1" | tr -cd '.' | wc -c | tr -d ' ')
  if [ -z "$1" ] || [ "$dots" != "2" ]; then
    echo "❌ 로그인 실패 ($2)"; echo "   응답: $1"
    echo "   → 계정 존재 여부와 QA_PASSWORD 값을 확인"; exit 1
  fi
}

# ── 요청 ──────────────────────────────────────────────────
req() { # method path [body]  → body\ncode
  if [ -n "$3" ]; then
    curl -s -w "\n%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$3"
  else
    curl -s -w "\n%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"
  fi
}
raw() { # 토큰 없이
  if [ -n "$3" ]; then
    curl -s -w "\n%{http_code}" -X "$1" "$BASE$2" -H "Content-Type: application/json" -d "$3"
  else
    curl -s -w "\n%{http_code}" -X "$1" "$BASE$2" -H "Content-Type: application/json"
  fi
}

# ── 판정 ──────────────────────────────────────────────────
# t/traw : 요청과 판정을 한 번에 (중첩 치환 없음). 결과는 LAST 에 보관
# check  : 이미 받아둔 응답을 판정 (test-category-hierarchy.sh 방식)
t() { # label expected method path [body]
  LAST=$(req "$3" "$4" "$5")
  local code=$(echo "$LAST" | tail -1)
  local body=$(echo "$LAST" | sed '$d')
  if [ "$code" = "$2" ]; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$2"
  else FAIL=$((FAIL+1)); printf "❌ %-6s expected %s got %s  → %s\n" "$1" "$2" "$code" "$body"; fi
}
traw() { # label expected method path [body]  — 토큰 없이
  LAST=$(raw "$3" "$4" "$5")
  local code=$(echo "$LAST" | tail -1)
  if [ "$code" = "$2" ]; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$2"
  else FAIL=$((FAIL+1)); printf "❌ %-6s expected %s got %s\n" "$1" "$2" "$code"; fi
}
check() { # label expected 응답
  local code=$(echo "$3" | tail -1)
  local body=$(echo "$3" | sed '$d')
  if [ "$code" = "$2" ]; then PASS=$((PASS+1)); printf "✅ %-4s %s [%s]\n" "$1" "$2" "$code"
  else FAIL=$((FAIL+1)); printf "❌ %-4s expected %s got %s  → %s\n" "$1" "$2" "$code" "$body"; fi
}
has() { # label 기대문자열   — 직전 LAST 검사
  local body=$(echo "$LAST" | sed '$d')
  if echo "$body" | grep -q "$2"; then PASS=$((PASS+1)); printf "✅ %-6s contains %s\n" "$1" "$2"
  else FAIL=$((FAIL+1)); printf "❌ %-6s '%s' 없음  → %s\n" "$1" "$2" "$body"; fi
}

# ── 값 비교 · DB 한 줄 ────────────────────────────────────
# test-withdraw → test-refresh 에 복제돼 있던 것을 세 번째(test-login-lock)가 생길 때 여기로 (ADR-041, 2026-09-30)
eq() { # label 기대값 실제값
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$2"
  else FAIL=$((FAIL+1)); printf "❌ %-6s expected [%s] got [%s]\n" "$1" "$2" "$3"; fi
}
dbv() { qa_mysql -N -e "$1" | tr '\t\n' '  ' | sed 's/ *$//'; }   # 결과를 한 줄로 — 칸(탭)·줄(개행) 모두 공백으로

# ── 값 꺼내기 ─────────────────────────────────────────────
jnum() { echo "$LAST" | sed '$d' | sed "s/.*\"$1\":\([0-9]*\).*/\1/"; }   # LAST 에서 필드명으로
num() { echo "$1" | sed '$d' | sed 's/.*"categoryNum":\([0-9]*\).*/\1/'; } # 넘겨받은 응답에서 categoryNum
neednum() {
  case "$1" in ''|*[!0-9]*) echo "❌ 기준 데이터 실패 ($2): $1"; exit 1;; esac
}

summary() {
  echo "═════════════════════════════"
  printf "PASS %d / FAIL %d\n" $PASS $FAIL
  # 실패가 하나라도 있으면 종료코드 1 — 이게 없으면 CI 게이트가 항상 통과한다 (ADR-042)
  [ "$FAIL" -eq 0 ] || exit 1
}
