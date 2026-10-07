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

    void updateLastLoginAt(@Param("userNum") Integer userNum);

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

    // ── 탈퇴 · 복구 · 파기 (ADR-052) ──────────────────────
    // 활동 계정만 'W' 로. 이미 탈퇴한 계정이면 0행
    int withdraw(@Param("userNum") Integer userNum);

    // 유예 중 로그인 = 탈퇴 취소. 그 사이 파기됐으면 0행
    int restore(@Param("userNum") Integer userNum);

    // 유예가 끝난 탈퇴 계정 번호 목록
    List<Integer> findPurgeTargets(@Param("graceDays") int graceDays);

    // 파기 직전 재확인 + 행 잠금. 목록을 뽑은 뒤 복구됐으면 null → 건너뛴다
    Integer lockPurgeTarget(@Param("userNum") Integer userNum, @Param("graceDays") int graceDays);

    // 물리적 삭제 — FK 순서대로 부른다 (UserService.purgeUser)
    void deleteTransactionsOf(@Param("userNum") Integer userNum);
    void deleteChildCategoriesOf(@Param("userNum") Integer userNum);
    void deleteCategoriesOf(@Param("userNum") Integer userNum);
	void deleteRefreshTokensOf(@Param("userNum") Integer userNum);
    void deleteUser(@Param("userNum") Integer userNum);

}