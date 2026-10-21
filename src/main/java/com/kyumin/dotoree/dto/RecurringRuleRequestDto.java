package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 반복 규칙 등록 · 수정 (ADR-067)
// 거래 규칙 = categoryNum (+ accountNum, 비우면 기본 '현금') / 이체 규칙 = fromAccountNum + toAccountNum — 둘 중 하나만
// 금액 규칙은 거래와 같다 — 평소 금액을 적고, 달마다 다르면 확인할 때 고친다(10/22)
@Getter
@Setter
@NoArgsConstructor
public class RecurringRuleRequestDto {

	private Integer categoryNum;
	private Integer accountNum;
	private Integer fromAccountNum;
	private Integer toAccountNum;

	@NotNull(message = "금액을 입력하시오.")
	@Max(value = 1000000000000L, message = "숫자는 1조원 이하 입력 가능합니다!")
	@Min(value = 1, message = "금액은 1원 이상 입력하여야 합니다!")
	private Long ruleAmount;

	@Size(max = 100, message = "메모는 100자 이하로 입력하시오")
	private String ruleMemo;

	@NotNull(message = "반복 주기를 고르시오.")
	@Pattern(regexp = "[MY]", message = "반복 주기는 매달(M) 또는 매년(Y)입니다.")
	private String repeatType;

	// 매년만 — 매달이면 무시한다
	@Min(value = 1, message = "달은 1~12 입니다.")
	@Max(value = 12, message = "달은 1~12 입니다.")
	private Integer repeatMonth;

	// 31 을 고르면 30일까지인 달엔 30일, 2월엔 28·29일
	@NotNull(message = "날짜(일)를 고르시오.")
	@Min(value = 1, message = "날짜는 1~31 입니다.")
	@Max(value = 31, message = "날짜는 1~31 입니다.")
	private Integer repeatDay;

	// 비우면 오늘. 지난 날은 안 된다 — 지난 예정이 한꺼번에 "밀린 예정"으로 쏟아지지 않게(지난 건 거래로 직접)
	private LocalDate startDate;

	// 비우면 계속(할부면 마지막 달)
	private LocalDate endDate;
}
