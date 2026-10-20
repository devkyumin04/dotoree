package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 계좌 — 돈이 들어 있는 곳(현금 · 통장 · 마이너스통장 · 카드). 잔액은 칸이 아니라 계산이다 (ADR-065)
@Getter
@Setter
@NoArgsConstructor
public class Account {

	public static final String DEFAULT_ACCOUNT_NAME = "현금";
	public static final String TYPE_CASH = "C";
	public static final String TYPE_BANK = "B";

	private Integer accountNum;
	private Integer userNum;
	private String accountName;
	private String accountType;
	private Long openingBalance;
	private Long creditLimit;
	private String isDefaultYn;
	private String useYn;
	private LocalDateTime createdAt;

	// 마이너스가 될 수 있는 계좌인가 — 현금 · 통장은 아니다(마이너스통장 · 카드만)
	public static boolean canBeNegative(String accountType) {
		return !TYPE_CASH.equals(accountType) && !TYPE_BANK.equals(accountType);
	}

	public boolean isOpen() {
		return "Y".equals(useYn);
	}

	public boolean isDefault() {
		return "Y".equals(isDefaultYn);
	}
}
