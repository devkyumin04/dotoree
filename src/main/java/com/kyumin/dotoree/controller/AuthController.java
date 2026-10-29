package com.kyumin.dotoree.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.EmailCodeRequestDto;
import com.kyumin.dotoree.dto.LoginResponseDto;
import com.kyumin.dotoree.dto.LoginResult;
import com.kyumin.dotoree.dto.PasswordResetRequestDto;
import com.kyumin.dotoree.dto.RefreshResponseDto;
import com.kyumin.dotoree.dto.RefreshResult;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.security.RefreshCookieProvider;
import com.kyumin.dotoree.service.EmailVerificationService;
import com.kyumin.dotoree.service.RefreshTokenService;
import com.kyumin.dotoree.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	// PATCH 일부수정 / PUT 전체수정 / GET 조회 / POST 등록 / DELETE 삭제

	private final RefreshTokenService refreshTokenService;
	private final RefreshCookieProvider refreshCookieProvider;
	private final EmailVerificationService emailVerificationService;
	private final UserService userService;

	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponseDto> refresh(@CookieValue(name = "refresh_token", required = false) String rawToken) {
		// 거절(빈 값)이면 401 — 서비스 트랜잭션이 커밋된 뒤라 재사용 탐지의 패밀리 폐기는 남는다 (ADR-062)
		RefreshResult refreshResult = refreshTokenService.refresh(rawToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_FAILED));
		ResponseCookie cookie = refreshCookieProvider.create(refreshResult.getRawRefreshToken());
		return ResponseEntity.status(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new RefreshResponseDto(refreshResult.getAccessToken()));
	}
	
	// 데모로 둘러보기 — 로그인과 같은 응답(Refresh 쿠키 + 본문의 Access). 비밀번호를 받지 않는다 (UserService.demoLogin)
	@PostMapping("/demo-login")
	public ResponseEntity<LoginResponseDto> demoLogin() {
		LoginResult loginResult = userService.demoLogin();
		ResponseCookie cookie = refreshCookieProvider.create(loginResult.getRawRefreshToken());
		return ResponseEntity.status(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(loginResult.getLoginResponse());
	}

	// 로그아웃 — 장부에서 일행 폐기(서비스) → 지우는 쿠키(Max-Age=0, 같은 이름·경로) + 204. 실패하지 않는다(쿠키 없음·장부에 없음도 204)
	// 서비스가 예외면 쿠키는 안 나간다(return 줄까지 못 감) — 장부가 살아 있는데 쿠키만 지우면 다시 폐기할 방법이 사라지므로 이 순서가 맞다
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String rawToken) {
		refreshTokenService.logout(rawToken);
		ResponseCookie cookie = refreshCookieProvider.expire();

		return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}

	// 인증 코드 받기 — 가입(S) · 비밀번호 재설정(P) (ADR-068). 보냈으면 204
	// 로그인 전에 부르므로 /api/auth/** permitAll 범위에 둔다(SecurityConfig)
	@PostMapping("/email-codes")
	public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequestDto requestDto) {
		emailVerificationService.sendCode(requestDto);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	// 비밀번호 재설정 — 주소 + 코드 + 새 비밀번호. 바뀌면 204, 화면은 로그인으로 보낸다
	@PostMapping("/password-reset")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequestDto requestDto) {
		userService.resetPassword(requestDto);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
