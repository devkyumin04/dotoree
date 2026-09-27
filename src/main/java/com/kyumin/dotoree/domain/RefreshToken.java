package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

	private Integer tokenNum;
	private String familyId;
	private Integer userNum;
	private String tokenHash;
	private LocalDateTime expiresAt;
	private String revokedYn;
	private LocalDateTime revokedAt;
	private LocalDateTime createdAt;

}
