package com.kyumin.dotoree.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.RefreshToken;

@Mapper
public interface RefreshTokenMapper {

	void insertRefreshToken(RefreshToken refreshToken);

	// 재발급 — 쿠키 원문의 해시로 장부 한 줄. 못 찾으면 null. 폐기·만료 판정은 서비스가
	RefreshToken findByTokenHash(@Param("tokenHash") String tokenHash);

	// 로테이션 — 찾은 줄(PK)을 폐기. 시각은 자바가 잰 값(유예 30초 판정과 같은 시계, ADR-055)
	// 0행 = 아직 살아 있을 때만 폐기하는데 그 사이 다른 요청이 먼저 폐기했다(동시 재발급) → 서비스가 401
	int revokeToken(@Param("tokenNum") Integer tokenNum, @Param("revokedAt") LocalDateTime revokedAt);

	// 재사용 탐지·유예 — 같은 familyId 중 살아 있는 줄을 전부 폐기 (ADR-055 선택 1·10)
	// 반환 = 폐기 직전까지 살아 있던 줄 수. 1 이상 = 진짜 재시도(유예 ②) → 새 카드 / 0 = 이미 전부 폐기됨(전체 폐기 직후) → 401
	int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") LocalDateTime revokedAt);

}
