package com.kyumin.dotoree.exception;

// 403 — 남의 것이다 (소유권 불일치)
// 의미별 부모 (ADR-041, 2026-09-30) — 새 예외가 이걸 상속하면 GlobalExceptionHandler 에 핸들러를 더하지 않는다.
// abstract: 직접 던지지 않는다. 자식 클래스명이 로그에서 출처를 알려 준다 (ADR-035)
public abstract class ForbiddenException extends RuntimeException {

	protected ForbiddenException(String message) {
		super(message);
	}
}
