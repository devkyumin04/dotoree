package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 아직 처리 안 한 예정 하나 — 규칙 + 예정일. overdue = 오늘보다 앞(밀린 예정 — 은행에선 이미 빠졌을 수 있다)
@Getter
@AllArgsConstructor
public class PendingOccurrenceDto {

	private LocalDate scheduledDate;
	private boolean overdue;
	private RecurringRuleResponseDto rule;
}
