package com.kyumin.dotoree.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// User 의 "잠겼나?" 판단 — DB · 스프링 없이 User 하나로 (단위 테스트)
// 지금 시각은 모두 2026-10-16 15:31 로 고정해 장면만 바꾼다
class UserTest {

	@Test
	void 잠긴_적_없으면_잠기지_않았다() {
		// given
		User user = new User();
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 15, 31);

		// when
		boolean result = user.isLockedAt(now);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void 잠금_시각_전이면_잠겼다() {
		// given
		User user = new User();
		user.setLockedUntil(LocalDateTime.of(2026, 10, 16, 16, 0));
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 15, 31);

		// when
		boolean result = user.isLockedAt(now);

		// then
		assertThat(result).isTrue();
	}

	@Test
	void 잠금_시각이_지나면_풀렸다() {
		// given
		User user = new User();
		user.setLockedUntil(LocalDateTime.of(2026, 10, 16, 15, 0));
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 15, 31);

		// when
		boolean result = user.isLockedAt(now);

		// then
		assertThat(result).isFalse();
	}
}
