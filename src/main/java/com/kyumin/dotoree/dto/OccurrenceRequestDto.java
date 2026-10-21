package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 예정 확인 · 건너뛰기 — 어느 예정인지(scheduledDate)
// 확인할 땐 실제 금액 · 날짜로 고칠 수 있다(10/22 — 카드값은 달마다 다르고, 주말이면 하루 이틀 다르다). 비우면 규칙 금액 · 예정일(미래면 오늘)
@Getter
@Setter
@NoArgsConstructor
public class OccurrenceRequestDto {

	@NotNull(message = "어느 예정인지(예정일)가 필요합니다.")
	private LocalDate scheduledDate;

	@Max(value = 1000000000000L, message = "숫자는 1조원 이하 입력 가능합니다!")
	@Min(value = 1, message = "금액은 1원 이상 입력하여야 합니다!")
	private Long amount;

	@PastOrPresent(message = "미래 날짜는 등록할 수 없습니다")
	private LocalDate actualDate;
}
