package com.kyumin.dotoree.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// Refresh 쿠키 전담 — 속성(HttpOnly·Secure·SameSite=Strict·Path=/api/auth·14일, ADR-055 선택 9)을 아는 곳은 여기 하나
// 서비스(RefreshTokenService)는 장부만, 쿠키(HTTP)는 여기 — 서비스가 웹 타입을 모르게 (2026-09-29)
// 로그인·재발급이 create 를 부르고, 5단계 로그아웃의 "지우는 쿠키"도 이 클래스에 둔다
@Component
public class RefreshCookieProvider {

	private static final String COOKIE_NAME = "refresh_token";
	private static final String COOKIE_PATH = "/api/auth";

	// 수명은 yml 한 줄이 원본 — RefreshTokenService(EXPIRES_AT)도 같은 키를 읽는다. 서로 의존하지 않고 값은 어긋날 수 없다
	private final Duration refreshTokenExpiration;

	public RefreshCookieProvider(@Value("${jwt.refresh-token-expiration}") Duration refreshTokenExpiration) {
		this.refreshTokenExpiration = refreshTokenExpiration;
	}

	// 원문을 담은 쿠키 — Set-Cookie 헤더에 붙이는 건 컨트롤러
	public ResponseCookie create(String rawRefreshToken) {
		return ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(COOKIE_PATH)
				.maxAge(refreshTokenExpiration)
				.build();
	}

	// 지우는 쿠키 — 서버는 브라우저 쿠키를 직접 못 지우니 같은 쿠키를 값 없이 Max-Age=0 으로 덮어쓴다(로그아웃)
	// 브라우저는 이름·도메인·경로로 쿠키를 구분 — 경로가 다르면 덮어쓰지 않고 한 장 더 생긴다. 그래서 create() 와 같은 상수·속성
	public ResponseCookie expire() {
		return ResponseCookie.from(COOKIE_NAME)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(COOKIE_PATH)
				.maxAge(0)
				.build();
	}
}
