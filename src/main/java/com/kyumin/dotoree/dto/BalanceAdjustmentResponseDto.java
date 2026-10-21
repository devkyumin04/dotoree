package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 목록은 MyBatis resultType 으로 직접 채운다 → 기본 생성자 + setter (TransferResponseDto 와 같은 예외, ADR-038)
@Getter
@Setter
@NoArgsConstructor
public class BalanceAdjustmentResponseDto {

	private Integer adjustmentNum;
	private Integer accountNum;
	private String accountName;
	private Long adjustmentAmount;
	private LocalDate adjustmentDate;
}
