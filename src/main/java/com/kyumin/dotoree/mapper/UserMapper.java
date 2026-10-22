package com.kyumin.dotoree.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.User;

@Mapper
public interface UserMapper {

    // 이메일 중복 확인용
    User findByEmail(@Param("email") String email);

    // 회원가입
    void insertUser(User user);
    
    User findByUserNum(@Param("userNum") Integer userNum);

    void updateLastLoginAt(@Param("userNum") Integer userNum, @Param("lastLoginAt") LocalDateTime lastLoginAt);

	// 실패 횟수 0 · 잠금 해제
	void resetLoginFail(@Param("userNum") Integer userNum);

	// 실패 횟수 +1
	void increaseLoginFailCount(@Param("userNum") Integer userNum);

	int lockIfFailLimitReached(@Param("userNum") Integer userNum, @Param("lockedUntil") LocalDateTime lockedUntil,
			@Param("maxFailCount") int maxFailCount);

    // ── 마이페이지 ─────────────────────────────────────
    // 활동 계정만. 0행 = 없거나 탈퇴한 계정
    int updateNickname(@Param("userNum") Integer userNum, @Param("nickname") String nickname);
	int updatePassword(@Param("userNum") Integer userNum, @Param("encodedPassword") String encodedPassword);

	// 비밀번호 재설정 — 새 비번 + 로그인 잠금 풀기. 상태('A'·'W') 상관없이 (ADR-068). 0행 = 없는 계정
	int resetPassword(@Param("userNum") Integer userNum, @Param("encodedPassword") String encodedPassword);

    // ── 탈퇴 · 복구 · 파기 (ADR-052) ──────────────────────
	// 사용자 탈퇴, 0행이면 업데이트된 행 없다는 의미(이미 탈퇴한 계정이면 0행)
	int withdraw(@Param("userNum") Integer userNum, @Param("withdrawnAt") LocalDateTime withdrawnAt);

	// 사용자 복구, 0행이면 업데이트된 행 없다는 의미(이미 복구된 계정이거나 그 사이 파기된 계정이면 0행)
	int restore(@Param("userNum") Integer userNum);

	// 유예가 끝난 탈퇴 계정 번호 목록 — purgeBefore(= 지금 - 유예 날수, 자바가 계산) 보다 먼저 탈퇴한 계정. 대상이 없으면 null 이 아니라 빈 목록
	List<Integer> findPurgeTargets(@Param("purgeBefore") LocalDateTime purgeBefore);

	// 파기 직전 재확인 + 행 잠금(FOR UPDATE). 아직 대상이면 번호, 그 사이 복구·재탈퇴됐으면 null → 건너뛴다
	// 잠금은 트랜잭션이 끝날 때 풀린다 — purgeUser 의 @Transactional 안에서 부른다
	Integer lockPurgeTarget(@Param("userNum") Integer userNum, @Param("purgeBefore") LocalDateTime purgeBefore);

	// 물리적 삭제 — 부르는 순서 = FK 순서 (UserService.purgeUser). 이유와 "새 테이블이 생기면" 은 UserMapper.xml
	void deleteOccurrencesOf(@Param("userNum") Integer userNum);

	void deleteRecurringRulesOf(@Param("userNum") Integer userNum);

	void deleteTransactionsOf(@Param("userNum") Integer userNum);

	void deleteTransfersOf(@Param("userNum") Integer userNum);

	void deleteAdjustmentsOf(@Param("userNum") Integer userNum);

	void deleteAccountsOf(@Param("userNum") Integer userNum);

	void deleteChildCategoriesOf(@Param("userNum") Integer userNum);

	void deleteCategoriesOf(@Param("userNum") Integer userNum);

	void deleteRefreshTokensOf(@Param("userNum") Integer userNum);

	// 그 계정 주소로 보낸 인증 코드 — FK 는 없지만 주소(개인정보)라 같이 지운다 (ADR-068)
	void deleteVerificationsOf(@Param("userNum") Integer userNum);

	void deleteUser(@Param("userNum") Integer userNum);

}