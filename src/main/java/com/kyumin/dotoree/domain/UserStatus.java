package com.kyumin.dotoree.domain;

// users.user_status
public enum UserStatus implements CodeEnum {

	ACTIVE("A"),		// 정상
	WITHDRAWN("W");		// 탈퇴 유예 중 — User.WITHDRAW_GRACE_DAYS 가 지나면 스케줄러가 파기한다 (ADR-052)

	private final String code;

	UserStatus(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		return code;
	}

	public static UserStatus fromCode(String code) {
		return CodeEnum.fromCode(UserStatus.class, code);
	}
}
