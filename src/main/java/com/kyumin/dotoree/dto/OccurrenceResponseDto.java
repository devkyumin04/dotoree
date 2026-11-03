package com.kyumin.dotoree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 처리한 예정 — 확인이면 만들어진 거래(transNum) 또는 이체(transferNum) 번호
@Getter
@AllArgsConstructor
public class OccurrenceResponseDto {

	private Integer ruleNum;
	private LocalDate scheduledDate;
	@Schema(description = "처리 결과 — C 확인(거래 · 이체가 만들어졌다) · S 건너뜀", allowableValues = {"C", "S"})
	private String status;
	private Integer transNum;
	private Integer transferNum;
}
