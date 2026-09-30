package com.kyumin.dotoree.exception;

// 로그인 실패 누적으로 잠긴 계정 — 잠긴 동안은 맞는 비밀번호도 거절한다. 423 Locked (ADR-057)
// 401 이 아닌 이유: 401 은 "자격 증명이 틀렸다"인데 잠금은 맞아도 거절이라 뜻이 다르고, 화면이 "왜 안 되는지"를 보여 줘야 한다
public class AccountLockedException extends RuntimeException {

	public AccountLockedException(String message) {
		super(message);
	}
}
