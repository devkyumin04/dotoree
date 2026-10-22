package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 이메일 인증 코드 한 통 — 가입(S) · 비밀번호 재설정(P). 칸 설명은 V10 (ADR-068)
@Getter
@Setter
@NoArgsConstructor
public class AuthVerification {

	public static final String PURPOSE_SIGNUP = "S";
	public static final String PURPOSE_PASSWORD_RESET = "P";

	private Integer verificationNum;
	private String email;
	private String purpose;
	private String codeHash;
	private Integer failCount;
	private LocalDateTime usedAt;
	private LocalDateTime createdAt;
}
