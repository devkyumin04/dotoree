package com.kyumin.dotoree.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 재발급 응답 본문 — 새 Access 하나. 칸 이름은 LoginResponseDto 와 같게(프론트가 같은 코드로 읽는다)
// Refresh 원문은 싣지 않는다 — 쿠키(HttpOnly)로만 나간다. 본문은 JS 가 읽을 수 있다 (ADR-055)
@Getter
@AllArgsConstructor
public class RefreshResponseDto {
	private String accessToken;
}
