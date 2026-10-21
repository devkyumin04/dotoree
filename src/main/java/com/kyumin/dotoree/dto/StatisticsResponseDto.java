package com.kyumin.dotoree.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatisticsResponseDto {
	private Long totalIncome;
	private Long totalExpense;
	private Long balance;
	private BigDecimal savingsRate;   // 저축률 (수입 − 지출) ÷ 수입 · 수입 0 이면 null
	private List<CategoryStatDto> expenseList;
	private List<CategoryStatDto> incomeList;
	private List<MonthlyStatDto> monthlyList;	
}
