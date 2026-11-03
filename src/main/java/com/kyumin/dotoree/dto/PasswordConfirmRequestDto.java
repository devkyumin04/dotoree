package com.kyumin.dotoree.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인한 사람에게 비밀번호를 한 번 더 받는 요청 — 탈퇴, 마이페이지 들어가기.
// 토큰만으로 되면 잠깐 자리를 비운 사이 남이 누를 수 있다
public record PasswordConfirmRequestDto(
	@NotBlank(message = "비밀번호를 입력해주세요")
	String password
) {}
