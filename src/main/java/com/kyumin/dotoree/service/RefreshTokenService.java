package com.kyumin.dotoree.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.kyumin.dotoree.domain.RefreshToken;
import com.kyumin.dotoree.mapper.RefreshTokenMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Refresh 토큰 장부 관리 — 발급 · 로테이션 · 재사용 탐지 · 폐기 (ADR-055)
// JwtTokenProvider 와 나눈 이유 — 저쪽은 서명 키로 JWT 를 만드는 곳. Refresh 는 서명 없는 무작위 값 + DB 장부라 재료가 다르다
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	// 원문 길이 32바이트 = 256비트 (ADR-055 선택 7 — OWASP 세션 토큰 기준 128비트 이상)
	private static final int TOKEN_BYTES = 32;

	// 값을 넣어 선언해서 @RequiredArgsConstructor 생성자 파라미터가 되지 않는다 (빈 주입 대상 아님)
	private final SecureRandom secureRandom = new SecureRandom();

	private final RefreshTokenMapper refreshTokenMapper;

	// yml 의 14d. final 이 아니라서 @RequiredArgsConstructor 생성자에 안 들어가고, 스프링이 @Value 로 채운다
	// @Getter — 컨트롤러가 쿠키 Max-Age 에 같은 값을 쓴다. Refresh 수명을 아는 클래스는 여기 하나
	@Value("${jwt.refresh-token-expiration}")
	@Getter
	private Duration refreshTokenExpiration;

	// 로그인 한 번 = 새 패밀리 (ADR-055 선택 2). 로그인·소셜 로그인은 이것만 부른다 — 패밀리 규칙은 이 클래스 안에만. 원문을 돌려준다 — 쿠키에 굽는 건 부르는 쪽
	public String issueNewFamily(Integer userNum) {
		String familyId = UUID.randomUUID().toString();
		return issue(userNum, familyId);
	}

	// 발급 부품 — 원문 만들기 → 해시 → INSERT → 원문 반환. issueNewFamily(새 familyId) · 로테이션(옛 familyId 복사) 이 부른다
	// private — 밖에서 아무 familyId 나 넘겨 발급하는 길을 막는다
	private String issue(Integer userNum, String familyId) {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		String tokenHash = hash(rawToken);
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setFamilyId(familyId);
		refreshToken.setUserNum(userNum);
		refreshToken.setTokenHash(tokenHash);
		refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshTokenExpiration));
		refreshTokenMapper.insertRefreshToken(refreshToken);
		return rawToken;
	}

	// 원문 → SHA-256 → hex 64글자. 발급(INSERT)과 로테이션(쿠키 원문으로 장부 찾기) 둘 다 쓴다
	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] tokenBytes = rawToken.getBytes(StandardCharsets.UTF_8);
			byte[] hashBytes = digest.digest(tokenBytes);
			return HexFormat.of().formatHex(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없음", e);
		}
	}

}
