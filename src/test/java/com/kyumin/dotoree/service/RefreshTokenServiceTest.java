package com.kyumin.dotoree.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.kyumin.dotoree.domain.RefreshToken;
import com.kyumin.dotoree.dto.RefreshResult;
import com.kyumin.dotoree.mapper.RefreshTokenMapper;
import com.kyumin.dotoree.security.JwtTokenProvider;

// Refresh 재사용 탐지 · 유예 30초 (ADR-055 선택 1·10) — 폐기된 카드가 다시 왔을 때의 네 장면 (단위 테스트)
// DB 대신 가짜 매퍼(Mockito), 지금 시각은 Clock.fixed 로 2026-10-20 12:00:00 고정. 장면마다 "언제 폐기됐나 · 일행에 살아 있는 카드 몇 장"만 바꾼다
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0, 0);
	private static final String FAMILY_ID = "family-1";
	private static final Integer USER_NUM = 7;

	@Mock
	private RefreshTokenMapper refreshTokenMapper;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
		refreshTokenService = new RefreshTokenService(refreshTokenMapper, jwtTokenProvider, clock);
		// 운영에선 스프링이 yml 로 채우는 @Value 필드 — 유예 장면의 새 카드 발급(issue)이 만료 시각 계산에 쓴다
		ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", Duration.ofDays(14));
	}

	@Test
	void 폐기_10초_뒤_재시도는_유예로_새_카드와_새_Access를_준다() {
		// given — 11:59:50 에 폐기 · 일행에 살아 있는 카드 1장 (응답을 못 받은 재시도 · 탭 두 개)
		givenRevokedToken(NOW.minusSeconds(10));
		when(refreshTokenMapper.revokeFamily(FAMILY_ID, NOW)).thenReturn(1);
		when(jwtTokenProvider.createAccessToken(USER_NUM)).thenReturn("new-access");

		// when
		Optional<RefreshResult> result = refreshTokenService.refresh("old-raw-token");

		// then — 새 Access + 새 Refresh 원문, 새 카드는 같은 일행·같은 사용자로 한 장
		assertThat(result).isPresent();
		assertThat(result.get().getAccessToken()).isEqualTo("new-access");
		assertThat(result.get().getRawRefreshToken()).isNotBlank();

		ArgumentCaptor<RefreshToken> inserted = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenMapper).insertRefreshToken(inserted.capture());
		assertThat(inserted.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
		assertThat(inserted.getValue().getUserNum()).isEqualTo(USER_NUM);
	}

	@Test
	void 폐기_40초_뒤에_오면_재사용으로_보고_일행_전체를_폐기한다() {
		// given — 11:59:20 에 폐기 · 살아 있는 카드 1장 (도둑이 훔친 옛 카드를 늦게 씀)
		givenRevokedToken(NOW.minusSeconds(40));
		when(refreshTokenMapper.revokeFamily(FAMILY_ID, NOW)).thenReturn(1);

		// when
		Optional<RefreshResult> result = refreshTokenService.refresh("old-raw-token");

		// then — 거절(빈 값) + 일행 전체 회수, 새 카드 · 새 Access 없음
		assertThat(result).isEmpty();
		verify(refreshTokenMapper).revokeFamily(FAMILY_ID, NOW);
		verify(refreshTokenMapper, never()).insertRefreshToken(any());
		verify(jwtTokenProvider, never()).createAccessToken(anyInt());
	}

	@Test
	void 일행에_살아_있는_카드가_없으면_30초_안이어도_거절한다() {
		// given — 11:59:50 에 폐기 · 살아 있는 카드 0장 (전체 폐기 직후 옛 카드로 부활 시도)
		givenRevokedToken(NOW.minusSeconds(10));
		when(refreshTokenMapper.revokeFamily(FAMILY_ID, NOW)).thenReturn(0);

		// when
		Optional<RefreshResult> result = refreshTokenService.refresh("old-raw-token");

		// then
		assertThat(result).isEmpty();
		verify(refreshTokenMapper, never()).insertRefreshToken(any());
	}

	@Test
	void 딱_30초면_유예가_끝나_거절한다() {
		// given — 11:59:30 에 폐기 · 살아 있는 카드 1장
		// 11:59:30 + 30초 = 12:00:00 → "12:00:00 이 12:00:00 보다 뒤냐?" 아니오 → 유예 아님
		givenRevokedToken(NOW.minusSeconds(30));
		when(refreshTokenMapper.revokeFamily(FAMILY_ID, NOW)).thenReturn(1);

		// when
		Optional<RefreshResult> result = refreshTokenService.refresh("old-raw-token");

		// then
		assertThat(result).isEmpty();
		verify(refreshTokenMapper, never()).insertRefreshToken(any());
	}

	// 장부에서 찾은 카드 — 이미 폐기('Y')됐고 만료는 아직. 원문이 무엇이든 이 카드가 나온다
	private void givenRevokedToken(LocalDateTime revokedAt) {
		RefreshToken token = new RefreshToken();
		token.setTokenNum(1);
		token.setFamilyId(FAMILY_ID);
		token.setUserNum(USER_NUM);
		token.setRevokedYn("Y");
		token.setRevokedAt(revokedAt);
		token.setExpiresAt(NOW.plusDays(14));
		when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(token);
	}
}
