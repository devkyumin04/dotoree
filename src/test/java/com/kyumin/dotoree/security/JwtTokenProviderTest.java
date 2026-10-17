package com.kyumin.dotoree.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

// Access 토큰 — 만든 토큰만 통과하고, 바꿔치기·다른 키·만료는 거절 (단위 테스트, 스프링 없이)
class JwtTokenProviderTest {

	// HS256 은 키가 32바이트 이상이어야 한다
	private static final String SECRET = "test_secret_key_for_unit_test_0123456789";

	private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, Duration.ofMinutes(15));

	@Test
	void 만든_토큰은_통과하고_사용자_번호를_다시_꺼낸다() {
		// given
		String token = provider.createAccessToken(42);

		// when · then
		assertThat(provider.validateToken(token)).isTrue();
		assertThat(provider.getUserNumFromToken(token)).isEqualTo(42);
	}

	@Test
	void 본문을_남의_번호로_바꿔치기한_토큰은_거절한다() {
		// given — 42번 토큰의 서명은 그대로 두고 본문(가운데 칸)만 99번 것으로
		String[] mine = provider.createAccessToken(42).split("\\.");
		String[] other = provider.createAccessToken(99).split("\\.");
		String forged = mine[0] + "." + other[1] + "." + mine[2];

		// when · then
		assertThat(provider.validateToken(forged)).isFalse();
	}

	@Test
	void 다른_키로_만든_토큰은_거절한다() {
		// given
		JwtTokenProvider stranger = new JwtTokenProvider("another_secret_key_for_unit_test_9876543210", Duration.ofMinutes(15));
		String token = stranger.createAccessToken(42);

		// when · then
		assertThat(provider.validateToken(token)).isFalse();
	}

	@Test
	void 만료된_토큰은_거절한다() {
		// given — 수명을 -1초로 만들면 만들자마자 만료
		JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, Duration.ofSeconds(-1));
		String token = expiredProvider.createAccessToken(42);

		// when · then
		assertThat(provider.validateToken(token)).isFalse();
	}
}
