package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 목록은 MyBatis resultType 으로 직접 채운다 → 기본 생성자 + setter (PersonalTransactionResponseDto 와 같은 예외, ADR-038)
@Getter
@Setter
@NoArgsConstructor
public class TransferResponseDto {

	private Integer transferNum;
	private Integer version;
	private Integer fromAccountNum;
	private String fromAccountName;
	private Integer toAccountNum;
	private String toAccountName;
	private Long transferAmount;
	private LocalDate transferDate;
	private String transferMemo;
}
