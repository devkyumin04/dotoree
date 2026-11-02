package com.kyumin.dotoree.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 처리한 예정 — C 확인(거래 · 이체가 생김) / S 건너뜀. 남은 예정 = 규칙에서 계산한 예정 − 이것 (ADR-067)
@Getter
@Setter
@NoArgsConstructor
public class RecurringOccurrence {

	private Integer occurrenceNum;
	private Integer ruleNum;
	private Integer userNum;
	private LocalDate scheduledDate;
	private OccurrenceStatus occurrenceStatus;
	private Integer transNum;
	private Integer transferNum;
	private LocalDateTime createdAt;
}
