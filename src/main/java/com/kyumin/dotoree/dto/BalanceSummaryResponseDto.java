package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 그날 기준 전체 잔액 합계 + 전날 대비 (ADR-065). 카드 · 마통의 마이너스도 더한다 — 갚을 돈을 뺀 내 돈
@Getter
@AllArgsConstructor
public class BalanceSummaryResponseDto {

	private LocalDate date;
	private long totalBalance;
	private long previousDayTotalBalance;
	// 그날 시작한 계좌의 시작 잔액은 빼고 센다 — 그래서 totalBalance − previousDayTotalBalance 와 다를 수 있다
	private long change;
}
