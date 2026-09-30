package com.kyumin.dotoree.exception;

// 새 비밀번호가 지금 비밀번호와 같다 — 바꾼 게 아니다. 400
public class SamePasswordException extends BadRequestException {

	public SamePasswordException(String message) {
		super(message);
	}
}
