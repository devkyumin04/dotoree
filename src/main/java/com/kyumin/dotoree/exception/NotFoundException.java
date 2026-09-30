package com.kyumin.dotoree.exception;

// 404 — 찾는 것이 없다 (카테고리·거래 …)
// 의미별 부모 (ADR-041, 2026-09-30) — 새 예외가 이걸 상속하면 GlobalExceptionHandler 에 핸들러를 더하지 않는다.
// abstract: 직접 던지지 않는다. 자식 클래스명이 로그에서 출처를 알려 준다 (ADR-035)
public abstract class NotFoundException extends RuntimeException {

	protected NotFoundException(String message) {
		super(message);
	}
}
