package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 계좌 — 돈이 들어 있는 곳(현금 · 통장 · 카드). 잔액은 칸이 아니라 계산이다 (ADR-065)
@Getter
@Setter
@NoArgsConstructor
public class Account {

	public static final String DEFAULT_ACCOUNT_NAME = "현금";
	public static final String TYPE_CASH = "C";

	private Integer accountNum;
	private Integer userNum;
	private String accountName;
	private String accountType;
	private Long openingBalance;
	private String isDefaultYn;
	private String useYn;
	private LocalDateTime createdAt;
}
