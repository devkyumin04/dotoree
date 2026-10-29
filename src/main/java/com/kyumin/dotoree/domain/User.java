package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class User {

    // 탈퇴 유예 기간. 이 날수가 지나면 스케줄러가 계정과 가계부를 물리적으로 삭제한다 (ADR-052)
    // 개인정보 처리방침(views/privacy.html)에 같은 숫자로 약속했다 — 바꾸면 방침도 같이
    public static final int WITHDRAW_GRACE_DAYS = 30;

    private Integer userNum;
    private String userPw;
    private String userNickname;
    private String userEmail;
    private String userStatus;
    private LocalDateTime userWithdrawnAt;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime userCreatedAt;
    private LocalDateTime userLastLoginAt;

	public boolean isLockedAt(LocalDateTime now) {
		if (lockedUntil == null) {
			return false;
		}
		return lockedUntil.isAfter(now);
	}

}