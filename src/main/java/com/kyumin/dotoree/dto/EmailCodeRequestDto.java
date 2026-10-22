package com.kyumin.dotoree.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 인증 코드 받기 — 주소 + 목적(S 가입 · P 비밀번호 재설정) (ADR-068)
@Getter
@Setter
@NoArgsConstructor
public class EmailCodeRequestDto {

	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다.")
	private String email;

	@NotBlank(message = "목적을 골라 주세요.")
	@Pattern(regexp = "^[SP]$", message = "목적은 S(가입) 또는 P(비밀번호 재설정)입니다.")
	private String purpose;
}
