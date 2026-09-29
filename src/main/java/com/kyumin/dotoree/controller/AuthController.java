package com.kyumin.dotoree.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.RefreshResponseDto;
import com.kyumin.dotoree.dto.RefreshResult;
import com.kyumin.dotoree.security.RefreshCookieProvider;
import com.kyumin.dotoree.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	// PATCH 일부수정 / PUT 전체수정 / GET 조회 / POST 등록 / DELETE 삭제

	private final RefreshTokenService refreshTokenService;
	private final RefreshCookieProvider refreshCookieProvider;

	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponseDto> refresh(@CookieValue(name = "refresh_token", required = false) String rawToken) {
		RefreshResult refreshResult = refreshTokenService.refresh(rawToken);
		ResponseCookie cookie = refreshCookieProvider.create(refreshResult.getRawRefreshToken());
		return ResponseEntity.status(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new RefreshResponseDto(refreshResult.getAccessToken()));
	}
	
	// 로그아웃 — 장부에서 일행 폐기(서비스) → 지우는 쿠키(Max-Age=0, 같은 이름·경로) + 204. 실패하지 않는다(쿠키 없음·장부에 없음도 204)
	// 서비스가 예외면 쿠키는 안 나간다(return 줄까지 못 감) — 장부가 살아 있는데 쿠키만 지우면 다시 폐기할 방법이 사라지므로 이 순서가 맞다
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String rawToken) {
		refreshTokenService.logout(rawToken);
		ResponseCookie cookie = refreshCookieProvider.expire();

		return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}
}
