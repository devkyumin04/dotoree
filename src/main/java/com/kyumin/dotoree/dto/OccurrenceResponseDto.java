package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 처리한 예정 — 확인이면 만들어진 거래(transNum) 또는 이체(transferNum) 번호
@Getter
@AllArgsConstructor
public class OccurrenceResponseDto {

	private Integer ruleNum;
	private LocalDate scheduledDate;
	private String status;
	private Integer transNum;
	private Integer transferNum;
}
