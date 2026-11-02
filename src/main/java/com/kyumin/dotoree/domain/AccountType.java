package com.kyumin.dotoree.domain;

// accounts.account_type (V4 · V5)
public enum AccountType implements CodeEnum {

	CASH("C"),			// 현금
	BANK("B"),			// 통장
	OVERDRAFT("M"),		// 마이너스통장
	CARD("K");			// 카드 (잔액이 마이너스 = 갚을 돈)

	private final String code;

	AccountType(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		return code;
	}

	// 마이너스가 될 수 있는 계좌인가 — 현금 · 통장은 아니다(마이너스통장 · 카드만)
	public boolean canBeNegative() {
		return this == OVERDRAFT || this == CARD;
	}

	public static AccountType fromCode(String code) {
		return CodeEnum.fromCode(AccountType.class, code);
	}
}
