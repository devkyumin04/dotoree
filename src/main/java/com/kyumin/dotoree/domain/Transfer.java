package com.kyumin.dotoree.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 이체 — 계좌 사이에서 돈이 자리만 옮기는 것(통장 → 현금, 카드값 내기). 수입도 지출도 아니다 (ADR-065)
@Getter
@Setter
@NoArgsConstructor
public class Transfer {

	private Integer transferNum;
	private Integer userNum;
	private Integer fromAccountNum;
	private Integer toAccountNum;
	private Long transferAmount;
	private LocalDate transferDate;
	private String transferMemo;
	private Integer version;
	private String useYn;
	private LocalDateTime createdAt;
}
