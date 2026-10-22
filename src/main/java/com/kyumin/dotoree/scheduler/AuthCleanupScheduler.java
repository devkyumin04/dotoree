package com.kyumin.dotoree.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kyumin.dotoree.service.EmailVerificationService;
import com.kyumin.dotoree.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 쪽 묵은 줄 정리 — 매일 한 번 (ADR-068)
//  · 하루 지난 인증 코드(auth_verifications) — 60초 · 24시간 5번을 세는 데 하루치만 있으면 된다. 주소(개인정보)를 오래 들고 있지 않는다
//  · 만료된 Refresh 장부(refresh_tokens) — 로그인 · 재발급마다 한 줄씩 쌓여 지우지 않으면 끝없이 는다
// 시각은 dotoree.auth-cleanup.cron — 기본 03:40 KST. 파기(03:30) 뒤 · 백업(04:00) 전
// 테스트는 기다리지 않고 cleanUp() 을 직접 부른다. 서버가 한 대라 중복 실행 방지는 없다 (ADR-002)
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthCleanupScheduler {

	private final EmailVerificationService emailVerificationService;
	private final RefreshTokenService refreshTokenService;

	@Scheduled(cron = "${dotoree.auth-cleanup.cron}", zone = "Asia/Seoul")
	public void cleanUp() {
		// 하나가 실패해도 다른 하나는 돈다 — 실패한 쪽은 다음 날 다시
		try {
			int codes = emailVerificationService.deleteOldCodes();
			log.info("인증 코드 정리 - 삭제 {}", codes);
		} catch (Exception e) {
			log.error("인증 코드 정리 실패", e);
		}
		try {
			int tokens = refreshTokenService.deleteExpired();
			log.info("만료 Refresh 정리 - 삭제 {}", tokens);
		} catch (Exception e) {
			log.error("만료 Refresh 정리 실패", e);
		}
	}
}
