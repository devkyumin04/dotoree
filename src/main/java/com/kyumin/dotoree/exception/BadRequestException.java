package com.kyumin.dotoree.exception;

// 400 — 요청 값 자체가 규칙에 안 맞는다 (예약어 이름 · 계층 위반 · 비밀번호 확인 실패 …)
// 의미별 부모 (ADR-041, 2026-09-30) — 새 예외가 이걸 상속하면 GlobalExceptionHandler 에 핸들러를 더하지 않는다.
// abstract: 직접 던지지 않는다. 자식 클래스명이 로그에서 출처를 알려 준다 (ADR-035)
public abstract class BadRequestException extends RuntimeException {

	protected BadRequestException(String message) {
		super(message);
	}
}
