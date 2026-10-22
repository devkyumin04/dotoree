package com.kyumin.dotoree.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequestDto {

	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message="이메일은 100자를 초과할 수 없습니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해주세요.")
	@Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
	@Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*$", message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.")
	private String password;

	// 메일로 받은 인증 코드 (ADR-068)
	@NotBlank(message = "인증 코드를 입력해주세요.")
	@Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 숫자 6자리입니다.")
	private String code;

	@NotBlank(message="닉네임을 입력해주세요")
	@Size(min = 1, max = 20, message = "닉네임은 1자 이상 20자 이하로 입력해주세요.")
	private String nickname;
}
