package com.kyumin.dotoree.domain;

import java.time.LocalDate;
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

	private Integer accountNum;
	private Integer userNum;
	private String accountName;
	private AccountType accountType;
	private Long openingBalance;
	private Long creditLimit;
	private LocalDate openingDate;
	private String isDefaultYn;
	private String useYn;
	private LocalDateTime createdAt;

	public boolean isOpen() {
		return "Y".equals(useYn);
	}

	public boolean isDefault() {
		return "Y".equals(isDefaultYn);
	}
}
