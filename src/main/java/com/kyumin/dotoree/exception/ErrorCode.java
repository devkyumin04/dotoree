package com.kyumin.dotoree.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 도메인 에러 목록 — 한 줄에 상태 코드와 문구를 같이 둔다 (ADR-062)
// 새 에러는 여기에 한 줄 + throw new BusinessException(ErrorCode.이름). 핸들러는 손대지 않는다
// 로그·디버깅에서 출처는 이름(TRANSACTION_NOT_FOUND)으로 찾는다 — 옛 도메인별 클래스 이름이 하던 일 (ADR-035 폐기)
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// ── 400 — 요청 값 자체가 규칙에 안 맞는다
	RESERVED_CATEGORY_NAME(HttpStatus.BAD_REQUEST, "사용할 수 없는 카테고리 이름입니다."),
	CATEGORY_SELF_PARENT(HttpStatus.BAD_REQUEST, "자기 자신을 상위로 지정할 수 없습니다."),
	CATEGORY_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "카테고리 타입이 불일치합니다."),
	CATEGORY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "카테고리는 2단계까지만 가능합니다."),
	CATEGORY_UNDER_DEFAULT(HttpStatus.BAD_REQUEST, "기본 카테고리 아래에는 만들 수 없습니다."),
	CATEGORY_HAS_CHILDREN_TO_SUB(HttpStatus.BAD_REQUEST, "하위 카테고리가 있으면 소분류로 바꿀 수 없습니다."),
	CATEGORY_HAS_CHILDREN(HttpStatus.BAD_REQUEST, "하위 카테고리를 먼저 삭제해주세요."),
	// 비번 틀림은 400 — 401 이면 apiClient.js 가 만료로 보고 재발급 뒤 로그인 화면으로 보낸다(오타 한 번에 로그아웃)
	// 비번 변경은 칸이 둘(현재·새)이라 "현재 비밀번호가…" 로 어느 칸인지 알린다
	PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
	CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
	SAME_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해주세요."),
	// 계좌(돈이 들어 있는 곳) — 아래 ACCOUNT_LOCKED(로그인 계정 잠금)와는 다른 뜻 (ADR-065)
	ACCOUNT_NEGATIVE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "현금·통장의 시작 잔액은 0 이상이어야 합니다."),
	ACCOUNT_CREDIT_LIMIT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "한도는 마이너스통장·카드에만 넣을 수 있습니다."),
	ACCOUNT_OVER_CREDIT_LIMIT(HttpStatus.BAD_REQUEST, "시작 잔액이 한도를 넘었습니다."),
	ACCOUNT_BALANCE_NOT_ZERO(HttpStatus.BAD_REQUEST, "잔액이 0 일 때만 닫을 수 있습니다. 잔액 맞추기나 이체로 0 으로 맞춘 뒤 닫아 주세요."),
	ACCOUNT_CLOSED(HttpStatus.BAD_REQUEST, "닫힌 계좌에는 거래를 넣을 수 없습니다."),
	TRANSFER_SAME_ACCOUNT(HttpStatus.BAD_REQUEST, "보내는 계좌와 받는 계좌가 같습니다."),
	ACCOUNT_HAS_RECURRING(HttpStatus.BAD_REQUEST, "이 계좌를 쓰는 반복이 있습니다. 반복도 같이 끝낼지 골라 주세요."),
	// 반복 예정 (ADR-067)
	RECURRING_TARGET_INVALID(HttpStatus.BAD_REQUEST, "거래(카테고리) 또는 이체(보내는 · 받는 계좌) 중 하나를 고르시오."),
	RECURRING_MONTH_REQUIRED(HttpStatus.BAD_REQUEST, "매년 반복은 달을 골라야 합니다."),
	RECURRING_START_IN_PAST(HttpStatus.BAD_REQUEST, "시작 날짜는 오늘 이후여야 합니다. 지난 건 거래로 직접 적어 주세요."),
	RECURRING_END_BEFORE_START(HttpStatus.BAD_REQUEST, "끝 날짜가 시작 날짜보다 앞입니다."),
	OCCURRENCE_NOT_SCHEDULED(HttpStatus.BAD_REQUEST, "그날은 이 반복의 예정일이 아닙니다."),
	// 이메일 인증 코드 (ADR-068) — 401 이 아니라 400: 401 이면 apiClient.js 가 만료로 본다
	// 틀림은 남은 기회를 넣어 throw 하는 쪽이 문구를 넘긴다. 만료 · 다 씀 · 5번 틀림 · 받은 적 없음은 한 문구 — 어느 쪽이든 할 일은 "새 코드 받기" 하나
	EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다."),
	EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료됐거나 더 쓸 수 없습니다. 새 코드를 받아 주세요."),

	// ── 401 — 누구인지 확인이 안 된다
	// 없음·불일치·파기됨을 한 문구로 — 어느 쪽인지 알려 주지 않는다 (계정 존재 비노출은 가입 409 때문에 이미 반쪽이지만, 굳이 더 열지 않는다)
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
	// 로그인한 사용자의 계정이 없을 때(탈퇴·파기 뒤 남은 토큰)
	USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),
	// 재발급 실패 — 없음·폐기·만료를 구분해 알려 주지 않는다(어느 쪽인지가 공격자에게 힌트가 된다)
	REFRESH_FAILED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

	// ── 403 — 남의 것이다 (소유권 불일치) · 바꿀 수 없는 것이다
	CATEGORY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 카테고리만 사용할 수 있습니다."),
	TRANSACTION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 거래내역만 사용할 수 있습니다."),
	DEFAULT_CATEGORY_NOT_EDITABLE(HttpStatus.FORBIDDEN, "기본 카테고리는 수정할 수 없습니다."),
	DEFAULT_CATEGORY_NOT_DELETABLE(HttpStatus.FORBIDDEN, "기본 카테고리는 삭제할 수 없습니다."),
	ACCOUNT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 계좌만 사용할 수 있습니다."),
	DEFAULT_ACCOUNT_NOT_CLOSABLE(HttpStatus.FORBIDDEN, "기본 계좌는 닫을 수 없습니다."),
	TRANSFER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 이체내역만 사용할 수 있습니다."),
	ADJUSTMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 잔액 보정만 사용할 수 있습니다."),
	RECURRING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 반복만 사용할 수 있습니다."),

	// ── 404 — 찾는 것이 없다
	CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카테고리입니다."),
	TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 거래내역입니다."),
	ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계좌입니다."),
	TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이체내역입니다."),
	ADJUSTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 잔액 보정입니다."),
	RECURRING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 반복입니다."),

	// ── 409 — 지금 상태와 부딪힌다
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	DUPLICATE_ACCOUNT_NAME(HttpStatus.CONFLICT, "같은 이름의 계좌가 이미 있습니다."),
	TRANSFER_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 이미 변경된 이체입니다. 새로고침 후 다시 시도해주세요."),
	OCCURRENCE_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 확인했거나 건너뛴 예정입니다. 새로고침 후 다시 확인해 주세요."),
	TRANSACTION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 이미 변경된 내역입니다. 새로고침 후 다시 시도해주세요."),

	// ── 429 — 너무 자주 (이메일 인증 코드 — SES 에 한 약속, ADR-068). 60초는 남은 초를 넣어 throw 하는 쪽이 문구를 넘긴다
	EMAIL_CODE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤 다시 받아 주세요."),
	EMAIL_CODE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "이 주소로는 24시간 동안 5번까지 받을 수 있습니다. 나중에 다시 시도해 주세요."),

	// ── 503 — 바깥 서비스(SES)가 안 된다. 원인은 로그(SesEmailSender)에
	EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요."),

	// ── 423 — 잠겼다. 실제 문구는 남은 분을 넣어 throw 하는 쪽이 넘긴다(UserService.buildLockedMessage). 아래는 기본값
	ACCOUNT_LOCKED(HttpStatus.LOCKED, "계정이 잠겼습니다. 잠시 뒤 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;
}
