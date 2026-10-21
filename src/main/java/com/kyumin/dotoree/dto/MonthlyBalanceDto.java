package com.kyumin.dotoree.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 그달 말일 기준 전체 잔액 합계 — 통계의 "월말 잔액 6개월" (ADR-065)
@Getter
@AllArgsConstructor
public class MonthlyBalanceDto {

	private String yearMonth;
	private long totalBalance;
}
