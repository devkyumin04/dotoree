#!/bin/bash
# 카테고리 삭제 시 거래 → 같은 타입 '미분류' 이관 QA (ADR-023)
# 사용법: export QA_PASSWORD='비밀번호'  후  sh qa/test-category-transfer.sh
# 전제: 서버 기동, test@test.com + rollback@test.com (둘 다 미분류 E/I 보유)
#
# 이 기능은 DELETE API 자체(404/403/400)가 아니라 '부수효과'가 검사 대상이다.
# 상태코드만 보면 전부 통과하므로 삭제 후 목록을 다시 조회해 body 를 검사한다.
#
# 주의: 중첩 명령치환 "$(req ... "body")" 은 macOS /bin/sh 에서 body 가 유실된다.
#       반드시 B=... 로 본문을 변수에 담고 t() 에 넘길 것. (트러블슈팅 8번)

. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"
ask_reset

# ── 목록 조회 + 거래 1건 검사 헬퍼 ────────────────────────────
# 응답이 JSON 배열이라 객체를 줄 단위로 쪼갠 뒤 transNum 으로 찾는다.
# (BSD sed 는 치환문에 \n 을 못 써서 awk 를 씀)
list() { LIST=$(req GET "/api/transactions?year=$YEAR&month=$MONTH" | sed '$d'); }
split() { echo "$LIST" | awk '{gsub(/},[ ]*\{/,"}\n{"); print}'; }
row() { split | grep -E "\"transNum\":$1[,}]"; }
rowhas() { # label transNum 기대정규식
  local r=$(row "$2")
  if [ -z "$r" ]; then FAIL=$((FAIL+1)); printf "❌ %-6s 거래 %s 가 목록에 없음\n" "$1" "$2"
  elif echo "$r" | grep -Eq "$3"; then PASS=$((PASS+1)); printf "✅ %-6s %s\n" "$1" "$3"
  else FAIL=$((FAIL+1)); printf "❌ %-6s '%s' 불일치  → %s\n" "$1" "$3" "$r"; fi
}
count() { split | grep -Ec '"transNum":[0-9]+' | tr -d ' '; }

mkcat() { # name type [parentNum] → LAST 에 응답
  if [ -n "$3" ]; then B="{\"categoryName\":\"$1\",\"categoryType\":\"$2\",\"parentCategoryNum\":$3}"
  else B="{\"categoryName\":\"$1\",\"categoryType\":\"$2\"}"; fi
  LAST=$(req POST /api/categories "$B")
}
mktrans() { # categoryNum amount memo → LAST 에 응답
  B="{\"categoryNum\":$1,\"transAmount\":$2,\"transDate\":\"$TODAY\",\"transMemo\":\"$3\"}"
  LAST=$(req POST /api/transactions "$B")
}

TODAY=$(date +%F)
YEAR=$(date +%Y); MONTH=$(date +%-m)
STAMP=$(date +%H%M%S)

TOKEN=$(login test@test.com);     checktoken "$TOKEN" test@test.com;     MAIN_TOKEN=$TOKEN
TOKEN=$(login rollback@test.com); checktoken "$TOKEN" rollback@test.com; OTHER_TOKEN=$TOKEN
TOKEN=$MAIN_TOKEN

# 미분류 번호 확보 (E/I) — 이관 목적지
LAST=$(req GET /api/categories)
CATS=$(echo "$LAST" | sed '$d' | awk '{gsub(/},[ ]*\{/,"}\n{"); print}')
DEF_E=$(echo "$CATS" | grep '"categoryName":"미분류"' | grep '"categoryType":"E"' | sed 's/.*"categoryNum":\([0-9]*\).*/\1/')
DEF_I=$(echo "$CATS" | grep '"categoryName":"미분류"' | grep '"categoryType":"I"' | sed 's/.*"categoryNum":\([0-9]*\).*/\1/')
neednum "$DEF_E" 지출미분류; neednum "$DEF_I" 수입미분류
echo "기준: 지출미분류=$DEF_E  수입미분류=$DEF_I  오늘=$TODAY  조회=$YEAR-$MONTH"; echo

echo "───── 정상 (경계값: 거래 건수)"
# 1. 거래 0건 카테고리 삭제 → 이관 0행이지만 정상. 목록 건수 불변
list; BEFORE=$(count)
mkcat "QA빈$STAMP" E; C0=$(jnum categoryNum); neednum "$C0" 빈카테고리
t 1-a 204 DELETE "/api/categories/$C0"
list; AFTER=$(count)
if [ "$BEFORE" = "$AFTER" ]; then PASS=$((PASS+1)); printf "✅ %-6s 목록 건수 불변 (%s)\n" 1-b "$AFTER"
else FAIL=$((FAIL+1)); printf "❌ %-6s 건수 변동 %s → %s\n" 1-b "$BEFORE" "$AFTER"; fi

# 2. 거래 1건 → 미분류로 이동 + version 0→1
mkcat "QA1건$STAMP" E; C1=$(jnum categoryNum); neednum "$C1" 1건카테고리
mktrans "$C1" 12000 이관1건; T1=$(jnum transNum); neednum "$T1" 거래1
has 2-a '"version":0'
t 2-b 204 DELETE "/api/categories/$C1"
list
rowhas 2-c "$T1" "\"categoryNum\":$DEF_E[,}]"
rowhas 2-d "$T1" '"categoryName":"미분류"'
rowhas 2-e "$T1" '"version":1'
rowhas 2-f "$T1" '"transType":"E"'

# 3. 거래 3건 → 전부 이동
mkcat "QA3건$STAMP" E; C3=$(jnum categoryNum); neednum "$C3" 3건카테고리
mktrans "$C3" 1000 셋1; T3A=$(jnum transNum)
mktrans "$C3" 2000 셋2; T3B=$(jnum transNum)
mktrans "$C3" 3000 셋3; T3C=$(jnum transNum)
t 3-a 204 DELETE "/api/categories/$C3"
list
rowhas 3-b "$T3A" "\"categoryNum\":$DEF_E[,}]"
rowhas 3-c "$T3B" "\"categoryNum\":$DEF_E[,}]"
rowhas 3-d "$T3C" "\"categoryNum\":$DEF_E[,}]"
LEFT=$(split | grep -Ec "\"categoryNum\":$C3[,}]" | tr -d ' ')
if [ "$LEFT" = "0" ]; then PASS=$((PASS+1)); printf "✅ %-6s 삭제 카테고리에 남은 거래 0건\n" 3-e
else FAIL=$((FAIL+1)); printf "❌ %-6s 아직 %s건이 카테고리 %s 에 남음\n" 3-e "$LEFT" "$C3"; fi
echo

echo "───── 정상 (경계값: 타입 E/I)"
# 5. 수입 카테고리 → 수입 미분류
mkcat "QA수입$STAMP" I; CI=$(jnum categoryNum); neednum "$CI" 수입카테고리
mktrans "$CI" 3000000 월급; TI=$(jnum transNum); neednum "$TI" 수입거래
t 5-a 204 DELETE "/api/categories/$CI"
list
rowhas 5-b "$TI" "\"categoryNum\":$DEF_I[,}]"
rowhas 5-c "$TI" '"transType":"I"'

# 6. 격리 — E·I 둘 다 있는 상태에서 E 만 삭제. I 거래는 불변
mkcat "QA격리E$STAMP" E; CX=$(jnum categoryNum); neednum "$CX" 격리E
mkcat "QA격리I$STAMP" I; CY=$(jnum categoryNum); neednum "$CY" 격리I
mktrans "$CX" 5000 격리지출; TX=$(jnum transNum)
mktrans "$CY" 7000 격리수입; TY=$(jnum transNum)
t 6-a 204 DELETE "/api/categories/$CX"
list
rowhas 6-b "$TX" "\"categoryNum\":$DEF_E[,}]"
rowhas 6-c "$TY" "\"categoryNum\":$CY[,}]"
rowhas 6-d "$TY" '"version":0'
echo

echo "───── 순서 의존 (이관이 version 을 올린 뒤)"
# 7. 삭제 전 version(0) 으로 수정 → 409  (ADR-023: 이게 맞는 동작)
B="{\"categoryNum\":$DEF_E,\"transAmount\":9999,\"transDate\":\"$TODAY\",\"transMemo\":\"옛버전\"}"
t 7 409 PUT "/api/transactions/$T1?version=0" "$B"
# 8. 새 version(1) 으로 수정 → 200, version 2
B="{\"categoryNum\":$DEF_E,\"transAmount\":9999,\"transDate\":\"$TODAY\",\"transMemo\":\"새버전\"}"
t 8-a 200 PUT "/api/transactions/$T1?version=1" "$B"
has 8-b '"version":2'

# 9. 이미 삭제된(use_yn='N') 거래는 이관 대상 아님
#    2건 중 1건을 먼저 삭제 → 카테고리 삭제 → 살아있는 1건만 이관
#    삭제된 건의 version 불변은 API 로 확인 불가(목록에 안 나옴) → 아래 SQL 로 수동 확인
mkcat "QA삭제$STAMP" E; C9=$(jnum categoryNum); neednum "$C9" 삭제섞인카테고리
mktrans "$C9" 1100 살아있음; T9A=$(jnum transNum)
mktrans "$C9" 2200 먼저삭제; T9B=$(jnum transNum)
t 9-a 204 DELETE "/api/transactions/$T9B?version=0"
t 9-b 204 DELETE "/api/categories/$C9"
list
rowhas 9-c "$T9A" "\"categoryNum\":$DEF_E[,}]"
rowhas 9-d "$T9A" '"version":1'
if [ -z "$(row "$T9B")" ]; then PASS=$((PASS+1)); printf "✅ %-6s 삭제 건은 목록에서 제외\n" 9-e
else FAIL=$((FAIL+1)); printf "❌ %-6s 삭제 건이 목록에 남음\n" 9-e; fi
echo

echo "───── 악의적 · 부수효과 (예외가 이관보다 앞인가)"
# 10. 남의 카테고리 삭제 → 403 + 남의 거래 불변
TOKEN=$OTHER_TOKEN
mkcat "QA타인$STAMP" E; CO=$(jnum categoryNum); neednum "$CO" 타인카테고리
mktrans "$CO" 4400 타인거래; TO=$(jnum transNum); neednum "$TO" 타인거래번호
TOKEN=$MAIN_TOKEN
t 10-a 403 DELETE "/api/categories/$CO"
TOKEN=$OTHER_TOKEN
list
rowhas 10-b "$TO" "\"categoryNum\":$CO[,}]"
rowhas 10-c "$TO" '"version":0'
TOKEN=$MAIN_TOKEN

# 11. 자식 있는 대분류 삭제 → 400 + 이관 없음
mkcat "QA부모$STAMP" E; CP=$(jnum categoryNum); neednum "$CP" 부모카테고리
mkcat "QA자식$STAMP" E "$CP"; CC=$(jnum categoryNum); neednum "$CC" 자식카테고리
mktrans "$CP" 6600 부모거래; TP=$(jnum transNum); neednum "$TP" 부모거래번호
t 11-a 400 DELETE "/api/categories/$CP"
list
rowhas 11-b "$TP" "\"categoryNum\":$CP[,}]"
rowhas 11-c "$TP" '"version":0'

# 12. 미분류 자체 삭제 → 403 (이관 목적지가 사라지면 안 됨)
t 12 403 DELETE "/api/categories/$DEF_E"
echo

summary
echo
echo "── 수동 확인 (9번: 삭제된 거래의 version 불변) ──"
echo "mysql -u root -p dotoree -e \"SELECT trans_num, category_num, version, use_yn FROM personal_transactions WHERE trans_num IN ($T9A, $T9B);\""
echo "기대: $T9A → category_num=$DEF_E, version=1, use_yn=Y / $T9B → category_num=$C9(이관 안 됨), version=1, use_yn=N"
