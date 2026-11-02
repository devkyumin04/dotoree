package com.kyumin.dotoree.domain;

// recurring_occurrences.occurrence_status (V9) — 예정은 저장하지 않고, 처리한 것(확인 · 건너뜀)만 기록한다
public enum OccurrenceStatus implements CodeEnum {

	CONFIRMED("C"),		// 확인 — 거래 · 이체가 만들어졌다
	SKIPPED("S");		// 건너뜀

	private final String code;

	OccurrenceStatus(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		return code;
	}

	public static OccurrenceStatus fromCode(String code) {
		return CodeEnum.fromCode(OccurrenceStatus.class, code);
	}
}
