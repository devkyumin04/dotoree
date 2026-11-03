package com.kyumin.dotoree.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 규칙은 SignupRequestDto.nickname 과 같아야 한다 — 가입 때 되는 값이 변경 때 안 되면 안 된다
public record NicknameRequestDto(
	@NotBlank(message = "닉네임을 입력해주세요")
	@Size(min = 1, max = 20, message = "닉네임은 1자 이상 20자 이하로 입력해주세요.")
	String nickname
) {}
