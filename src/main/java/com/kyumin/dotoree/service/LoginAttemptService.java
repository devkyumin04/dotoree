package com.kyumin.dotoree.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

// 로그인 실패 기록 — login() 의 트랜잭션과 따로 열고 바로 커밋한다 (REQUIRES_NEW, ADR-062)
// login() 이 거절(401·423)을 던지며 롤백해도 이 기록은 남아야 잠금이 걸린다. 그 밖의 login() 쓰기(탈퇴 취소·성공 기록)는 보통처럼 같이 롤백
// 따로 클래스인 이유 — 같은 클래스 안에서 부르면 스프링 프록시를 거치지 않아 REQUIRES_NEW 가 무시된다
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

	private final UserMapper userMapper;

	// 잠금 시간이 지난 계정 — 실패 횟수·잠금을 푼다
	// 바깥(login)에서 하면 바깥이 이 사람 줄을 잠근 채로 recordFailure 가 같은 줄을 쓰려 해 서로 기다린다(데드락). 먼저 커밋돼도 해가 없다
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void resetLoginFail(Integer userNum) {
		userMapper.resetLoginFail(userNum);
	}

	// 실패 +1 → 한도를 채웠으면 잠금. 이번에 잠갔으면 true
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean recordFailure(Integer userNum, LocalDateTime lockedUntil, int maxFailCount) {
		userMapper.increaseLoginFailCount(userNum);
		int lockedRows = userMapper.lockIfFailLimitReached(userNum, lockedUntil, maxFailCount);
		return lockedRows == 1;
	}
}
