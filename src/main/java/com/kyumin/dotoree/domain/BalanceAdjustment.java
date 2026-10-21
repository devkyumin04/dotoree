package com.kyumin.dotoree.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 잔액 보정 — 실제 잔액과 앱 잔액의 차이를 메우는 한 줄. 번 돈도 쓴 돈도 아니라 통계엔 안 들어가고 잔액에만 더한다 (ADR-066)
@Getter
@Setter
@NoArgsConstructor
public class BalanceAdjustment {

	private Integer adjustmentNum;
	private Integer userNum;
	private Integer accountNum;
	private Long adjustmentAmount;
	private LocalDate adjustmentDate;
	private String useYn;
	private LocalDateTime createdAt;
}
