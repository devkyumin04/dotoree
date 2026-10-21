package com.kyumin.dotoree.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 잔액 맞추기 — 사용자는 차이가 아니라 "은행 앱에 보이는 실제 잔액"을 넣는다. 차이는 서버가 계산한다
// 카드 · 마이너스통장은 마이너스가 될 수 있어 아래로도 1조까지
@Getter
@Setter
@NoArgsConstructor
public class BalanceAdjustmentRequestDto {

	@NotNull(message = "실제 잔액을 입력하시오.")
	@Max(value = 1000000000000L, message = "숫자는 1조원 이하 입력 가능합니다!")
	@Min(value = -1000000000000L, message = "숫자는 -1조원 이상 입력 가능합니다!")
	private Long actualBalance;
}
