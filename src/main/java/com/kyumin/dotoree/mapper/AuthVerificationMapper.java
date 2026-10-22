package com.kyumin.dotoree.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.AuthVerification;

// 이메일 인증 코드 (ADR-068). 시각은 전부 자바가 넘긴다 (ADR-060)
@Mapper
public interface AuthVerificationMapper {

	void insertVerification(AuthVerification verification);

	// 이 주소로 마지막에 보낸 시각 — 목적(S·P) 상관없이. 보낸 적 없으면 null (60초 간격)
	LocalDateTime findLastSentAt(@Param("email") String email);

	// since 뒤로 이 주소에 보낸 수 — 목적 상관없이 (하루 5번 — 가입 + 재설정 합쳐서)
	int countSentSince(@Param("email") String email, @Param("since") LocalDateTime since);

	// 이 주소 · 목적으로 마지막에 보낸 코드. 새로 받으면 옛 코드는 여기 안 잡혀 무효가 된다. 없으면 null
	AuthVerification findLatest(@Param("email") String email, @Param("purpose") String purpose);

	// 틀린 횟수 +1 — 가입 · 재설정 트랜잭션과 따로 커밋한다(EmailCodeAttemptService)
	void increaseFailCount(@Param("verificationNum") Integer verificationNum);

	// 사용 처리. 아직 안 쓴 줄만 — 0행 = 같은 코드로 동시에 온 다른 요청이 먼저 썼다
	int markUsed(@Param("verificationNum") Integer verificationNum, @Param("usedAt") LocalDateTime usedAt);

	// 정리 — before 보다 먼저 보낸 줄을 지운다. 지운 줄 수
	int deleteSentBefore(@Param("before") LocalDateTime before);
}
