package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 계좌 등록 · 수정. 종류별 규칙(현금·통장은 0 이상, 한도는 마이너스통장·카드만)은 서비스가 본다 — 칸 하나로는 못 보는 규칙이라
@Getter
@Setter
@NoArgsConstructor
public class AccountRequestDto {

	@NotBlank(message = "계좌 이름을 입력하시오.")
	@Size(max = 20, message = "계좌 이름은 20자 이하로 입력하시오")
	private String accountName;

	@NotBlank(message = "계좌 종류를 지정하시오.")
	@Pattern(regexp = "[CBMK]", message = "잘못된 계좌 종류입니다!")
	private String accountType;

	@NotNull(message = "시작 잔액을 입력하시오.")
	@Max(value = 1000000000000L, message = "시작 잔액은 1조원 이하로 입력하시오")
	@Min(value = -1000000000000L, message = "시작 잔액은 -1조원 이상으로 입력하시오")
	private Long openingBalance;

	// 시작 잔액이 며칠 기준인지 — 비우면 등록은 오늘, 수정은 지금 값 그대로 (ADR-065 ⑦)
	@PastOrPresent(message = "시작 날짜는 미래로 정할 수 없습니다")
	private LocalDate openingDate;

	@Positive(message = "한도는 1원 이상으로 입력하시오")
	@Max(value = 1000000000000L, message = "한도는 1조원 이하로 입력하시오")
	private Long creditLimit;

	// 카테고리 이름과 같은 이유로 입구에서 한 번만 앞뒤 공백 · 전각 공백 제거 (CategoryRequestDto)
	public void setAccountName(String accountName) {
		this.accountName = (accountName == null) ? null : accountName.strip();
	}
}
