package com.kyumin.dotoree.exception;

// 로그인한 사용자의 비밀번호 재확인 실패 (탈퇴 등). 400 으로 보낸다 —
// 401 이면 apiClient.js 가 토큰을 지우고 로그인 화면으로 보내서, 비번 한 번 틀렸다고 로그아웃된다
public class PasswordMismatchException extends BadRequestException {

	public PasswordMismatchException(String message) {
		super(message);
	}
}
