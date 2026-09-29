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
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.RefreshToken;
import com.kyumin.dotoree.dto.RefreshResult;
import com.kyumin.dotoree.exception.InvalidCredentialsException;
import com.kyumin.dotoree.mapper.RefreshTokenMapper;
import com.kyumin.dotoree.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

// Refresh 토큰 장부 관리 — 발급 · 로테이션 · 재사용 탐지 · 폐기 (ADR-055)
// JwtTokenProvider 와 나눈 이유 — 저쪽은 서명 키로 JWT 를 만드는 곳. Refresh 는 서명 없는 무작위 값 + DB 장부라 재료가 다르다
// 재발급(refresh)만 새 Access 까지 여기서 만든다(JwtTokenProvider 주입) — 로그인과 달리 다른 절차 없는 순수한 토큰 교환이라 한 메서드에 모은다 (2026-09-28)
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	// 원문 길이 32바이트 = 256비트 (ADR-055 선택 7 — OWASP 세션 토큰 기준 128비트 이상)
	private static final int TOKEN_BYTES = 32;

	// 재발급 실패 문구 하나 — 없음·폐기·만료를 구분해 알려 주지 않는다(어느 쪽인지가 공격자에게 힌트가 된다). 세 곳이 같이 쓴다
	private static final String REFRESH_FAIL_MESSAGE = "로그인이 만료되었습니다. 다시 로그인해 주세요.";

	// 값을 넣어 선언해서 @RequiredArgsConstructor 생성자 파라미터가 되지 않는다 (빈 주입 대상 아님)
	private final SecureRandom secureRandom = new SecureRandom();

	private final RefreshTokenMapper refreshTokenMapper;

	// 새 Access(팔찌) 만들기만 빌려 쓴다 — refresh() ⑥
	private final JwtTokenProvider jwtTokenProvider;

	// yml 의 14d. final 이 아니라서 @RequiredArgsConstructor 생성자에 안 들어가고, 스프링이 @Value 로 채운다
	// 발급 때 EXPIRES_AT 계산에 쓴다. 쿠키 Max-Age 는 RefreshCookieProvider 가 같은 yml 키를 읽는다(원본은 yml 한 줄)
	@Value("${jwt.refresh-token-expiration}")
	private Duration refreshTokenExpiration;

	// 로그인 한 번 = 새 패밀리 (ADR-055 선택 2). 로그인·소셜 로그인은 이것만 부른다 — 패밀리 규칙은 이 클래스 안에만. 원문을 돌려준다 — 쿠키에 굽는 건 부르는 쪽
	public String issueNewFamily(Integer userNum) {
		String familyId = UUID.randomUUID().toString();
		return issue(userNum, familyId);
	}

	// 재발급 = 로테이션 — 옛 카드 폐기 + 같은 일행(familyId)으로 새 카드 + 새 Access. 한 트랜잭션 (ADR-055)
	// 받는 것: 쿠키 원문 / 돌려주는 것: RefreshResult(새 Access + 새 Refresh 원문). 쿠키 굽기·본문은 컨트롤러가
	@Transactional
	public RefreshResult refresh(String rawToken) {

		if (rawToken == null || rawToken.isEmpty()) {
			throw new InvalidCredentialsException(REFRESH_FAIL_MESSAGE);
		}

		String tokenHash = hash(rawToken);
		RefreshToken refreshToken = refreshTokenMapper.findByTokenHash(tokenHash);

		if(refreshToken == null) {
			throw new InvalidCredentialsException(REFRESH_FAIL_MESSAGE);
		}
		if ("Y".equals(refreshToken.getRevokedYn())) {
			// 4단계: 재사용 탐지(패밀리 전체 폐기) · 유예 30초로 바뀜 (ADR-055)
			throw new InvalidCredentialsException(REFRESH_FAIL_MESSAGE);
		}
		if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new InvalidCredentialsException(REFRESH_FAIL_MESSAGE);
		}

		int revokedCount = refreshTokenMapper.revokeToken(refreshToken.getTokenNum(), LocalDateTime.now());
		if (0 == revokedCount) {
			throw new InvalidCredentialsException(REFRESH_FAIL_MESSAGE);
		}
		
		String rawRefreshToken = issue(refreshToken.getUserNum(), refreshToken.getFamilyId());
		String accessToken = jwtTokenProvider.createAccessToken(refreshToken.getUserNum());
		return new RefreshResult(accessToken, rawRefreshToken);
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
