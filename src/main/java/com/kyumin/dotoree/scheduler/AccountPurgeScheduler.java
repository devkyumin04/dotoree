package com.kyumin.dotoree.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kyumin.dotoree.domain.User;
import com.kyumin.dotoree.mapper.UserMapper;
import com.kyumin.dotoree.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 유예(30일)가 끝난 탈퇴 계정을 매일 물리적으로 삭제한다 (ADR-052)
// 시각은 dotoree.purge.cron — 기본 03:30 KST. 04:00 DB 백업(ADR-050) 전에 돌아서 그날 백업부터 지워진 상태가 담긴다.
// CI 는 환경변수 DOTOREE_PURGE_CRON 으로 몇 초마다 돌려 qa/test-withdraw.sh 가 파기까지 확인한다
// 서버가 한 대라 중복 실행 방지(ShedLock 등)는 없다 — 서버가 늘면 그때 (ADR-002)
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountPurgeScheduler {

	private final UserMapper userMapper;
	private final UserService userService;

	@Scheduled(cron = "${dotoree.purge.cron}", zone = "Asia/Seoul")
	public void purgeWithdrawnUsers() {

		List<Integer> targets = userMapper.findPurgeTargets(User.WITHDRAW_GRACE_DAYS);

		if (targets.isEmpty()) {
			return;
		}

		int purged = 0;
		int failed = 0;

		for (Integer userNum : targets) {
			try {
				if (userService.purgeUser(userNum)) {
					purged++;
				}
			} catch (Exception e) {
				// 한 계정이 실패해도 나머지는 계속. 실패한 계정은 'W' 로 남아 다음 날 다시 대상이 된다
				// 로그엔 번호만 — 이메일은 남기지 않는다
				failed++;
				log.error("탈퇴 계정 파기 실패 userNum={}", userNum, e);
			}
		}

		log.info("탈퇴 계정 파기 - 대상 {} / 삭제 {} / 실패 {}", targets.size(), purged, failed);
	}
}
