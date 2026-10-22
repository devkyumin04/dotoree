package com.kyumin.dotoree.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.mapper.AuthVerificationMapper;

import lombok.RequiredArgsConstructor;

// 인증 코드 틀린 횟수 기록 — 가입 · 재설정 트랜잭션과 따로 열고 바로 커밋한다 (REQUIRES_NEW, ADR-062 · 068)
// 코드가 틀리면 가입 · 재설정은 400 을 던지며 롤백된다. +1 이 같이 롤백되면 횟수가 영영 0 이라 "5번 틀리면 버림"이 안 걸린다
// 따로 클래스인 이유 — 같은 클래스 안에서 부르면 스프링 프록시를 거치지 않아 REQUIRES_NEW 가 무시된다 (LoginAttemptService 와 같음)
// 바깥 트랜잭션은 이 줄을 읽기만 하고 잠그지 않는다(FOR UPDATE 없음) — 잠그면 여기서 같은 줄을 쓰려다 서로 기다린다
@Service
@RequiredArgsConstructor
public class EmailCodeAttemptService {

	private final AuthVerificationMapper authVerificationMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(Integer verificationNum) {
		authVerificationMapper.increaseFailCount(verificationNum);
	}
}
