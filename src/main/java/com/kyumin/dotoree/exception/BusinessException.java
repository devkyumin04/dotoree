package com.kyumin.dotoree.exception;

import lombok.Getter;

// 도메인 예외는 이것 하나 — 무엇이 잘못됐는지는 ErrorCode 가 말한다 (ADR-062)
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	// 보통 — 문구도 ErrorCode 것 그대로
	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	// 요청마다 문구가 달라질 때만 (잠금의 남은 분 등) — 상태는 ErrorCode, 문구는 부르는 쪽
	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
