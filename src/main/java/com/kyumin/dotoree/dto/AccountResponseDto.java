package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 계좌 + 지금 잔액. 목록은 MyBatis resultType 으로 직접 채운다 → 기본 생성자 + setter (PersonalTransactionResponseDto 와 같은 예외, ADR-038)
@Getter
@Setter
@NoArgsConstructor
public class AccountResponseDto {

	private Integer accountNum;
	private String accountName;
	private String accountType;
	private Long openingBalance;
	private Long creditLimit;
	private LocalDate openingDate;
	private Boolean isDefault;
	private Long balance;
}
