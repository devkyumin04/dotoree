package com.kyumin.dotoree.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 판단에 쓰는 "지금" 은 전부 이 시계에서 읽는다 (ADR-060)
// 운영은 진짜 시계(JVM 기본 시간대 — 지금까지의 LocalDateTime.now() 와 같은 값),
// 단위 테스트는 Clock.fixed(...) 로 갈아 끼워 "잠긴 지 31분 뒤" 같은 장면을 기다리지 않고 만든다
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
