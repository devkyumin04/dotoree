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

}
