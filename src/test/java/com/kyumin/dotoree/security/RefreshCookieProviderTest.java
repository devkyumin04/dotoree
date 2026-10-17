package com.kyumin.dotoree.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

// Refresh 쿠키 속성 (ADR-055 선택 9) — 하나라도 빠지면 탈취·전송 범위가 넓어진다 (단위 테스트)
class RefreshCookieProviderTest {

	private final RefreshCookieProvider provider = new RefreshCookieProvider(Duration.ofDays(14));

	@Test
	void 발급_쿠키는_JS가_못_읽고_HTTPS로_같은_사이트의_인증_경로에만_간다() {
		// when
		ResponseCookie cookie = provider.create("raw-token");

		// then
		assertThat(cookie.getName()).isEqualTo("refresh_token");
		assertThat(cookie.getValue()).isEqualTo("raw-token");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Strict");
		assertThat(cookie.getPath()).isEqualTo("/api/auth");
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
	}

	@Test
	void 지우는_쿠키는_같은_이름과_경로에_값_없이_수명_0() {
		// when
		ResponseCookie cookie = provider.expire();

		// then — 이름·경로가 다르면 브라우저가 덮어쓰지 않고 한 장 더 만든다
		assertThat(cookie.getName()).isEqualTo("refresh_token");
		assertThat(cookie.getPath()).isEqualTo("/api/auth");
		assertThat(cookie.getValue()).isEmpty();
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
	}
}
