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
}
