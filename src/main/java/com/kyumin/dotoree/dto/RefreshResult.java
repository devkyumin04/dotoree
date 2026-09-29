package com.kyumin.dotoree.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 재발급 결과 봉투 — RefreshTokenService → AuthController (서버 안쪽 전용, JSON 으로 나가지 않는다)
// 컨트롤러가 나눈다: 새 Access → 본문 / 새 Refresh 원문 → 쿠키. 해시는 DB 에만 있고 여기 싣지 않는다
@Getter
@AllArgsConstructor
public class RefreshResult {

	private String accessToken;
	private String rawRefreshToken;

}
