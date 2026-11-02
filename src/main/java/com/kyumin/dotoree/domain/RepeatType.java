package com.kyumin.dotoree.domain;

// recurring_rules.repeat_type (V9)
public enum RepeatType implements CodeEnum {

	MONTHLY("M"),	// 매달
	YEARLY("Y");	// 매년 — repeat_month 가 함께 필요하다

	private final String code;

	RepeatType(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		return code;
	}

	public static RepeatType fromCode(String code) {
		return CodeEnum.fromCode(RepeatType.class, code);
	}
}
