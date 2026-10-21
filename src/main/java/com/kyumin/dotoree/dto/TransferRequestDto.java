package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 이체 등록 · 수정 — 금액 · 날짜 규칙은 거래와 같다(PersonalTransactionRequestDto)
@Getter
@Setter
@NoArgsConstructor
public class TransferRequestDto {

	@NotNull(message = "보내는 계좌를 선택하시오.")
	private Integer fromAccountNum;

	@NotNull(message = "받는 계좌를 선택하시오.")
	private Integer toAccountNum;

	@NotNull(message = "이체 금액을 입력하시오.")
	@Max(value = 1000000000000L, message = "숫자는 1조원 이하 입력 가능합니다!")
	@Min(value = 1, message = "금액은 1원 이상 입력하여야 합니다!")
	private Long transferAmount;

	@NotNull(message = "날짜는 필수로 지정해야 합니다!")
	@PastOrPresent(message = "미래 날짜는 등록할 수 없습니다")
	private LocalDate transferDate;

	@Size(max = 100, message = "메모는 100자 이하로 입력하시오")
	private String transferMemo;
}
