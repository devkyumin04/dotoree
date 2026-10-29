package com.kyumin.dotoree.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 읽기 전용 데모 계정 — 이메일 하나로 구분한다(설정 dotoree.demo.email, 비우면 기능 꺼짐)
// 이 계정은 비밀번호로 로그인하지 않는다: "데모로 둘러보기" 버튼(POST /api/auth/demo-login)만 문을 연다. 쓰기 요청은 DemoGuardFilter 가 막는다
@Component
public class DemoAccount {

	private final String email;

	public DemoAccount(@Value("${dotoree.demo.email:}") String email) {
		this.email = email == null ? "" : email.trim();
	}

	public boolean isEnabled() {
		return !email.isEmpty();
	}

	public String email() {
		return email;
	}

	public boolean isDemo(String candidateEmail) {
		return isEnabled() && email.equalsIgnoreCase(candidateEmail);
	}
}
