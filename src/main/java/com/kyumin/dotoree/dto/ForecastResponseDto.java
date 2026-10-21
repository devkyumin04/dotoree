package com.kyumin.dotoree.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 예상 잔액 — 지금 잔액 + 남은 예정(밀린 것 포함)을 untilDate 까지 (ADR-067)
// 합계 + 계좌별. 계좌별엔 그 사이 가장 낮아지는 날과 그때 잔액 — "11/1 국민통장 −200,000" 을 미리 보게
@Getter
@AllArgsConstructor
public class ForecastResponseDto {

	private LocalDate untilDate;
	private long currentTotal;
	private long forecastTotal;
	private List<AccountForecast> accounts;

	@Getter
	@AllArgsConstructor
	public static class AccountForecast {
		private Integer accountNum;
		private String accountName;
		private String accountType;
		private long balance;
		private long forecastBalance;
		private long lowestBalance;
		private LocalDate lowestDate;
		// 모자람 — 현금 · 통장이 0 아래로, 마이너스통장 · 카드가 한도 밖으로 (한도가 없으면 모자람 없음)
		private boolean shortage;
	}
}
