package com.kyumin.dotoree.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 로그인 결과 봉투 — UserService → UserController (서버 안쪽 전용, JSON 으로 나가지 않는다)
// 컨트롤러가 나눈다: 안내문(LoginResponseDto) → 본문 / Refresh 원문 → 쿠키
@Getter
@AllArgsConstructor
public class LoginResult {

	private LoginResponseDto loginResponse;
	private String rawRefreshToken;

}
