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

@Getter
@Setter
@NoArgsConstructor
public class PersonalTransactionRequestDto {
	
	@NotNull(message = "카테고리는 필수로 선택해야 합니다!")
	private Integer categoryNum;
	
	@NotNull(message = "거래내역의 금액은 필수로 입력해야 합니다!")
	@Max(value = 1000000000000L, message = "숫자는 1조원 이하 입력 가능합니다!")
	@Min(value = 1, message = "금액은 1원 이상 입력하여야 합니다!")
	private Long transAmount;
	
	@NotNull(message = "날짜는 필수로 지정해야 합니다!")
	@PastOrPresent(message = "미래 날짜는 등록할 수 없습니다")
	private LocalDate transDate;
	
	@Size(max=100, message = "메모는 100자 이하로 입력하시오")
	private String transMemo;
	
	// 비우면 등록은 기본 계좌, 수정은 지금 계좌 그대로 (ADR-065)
	private Integer accountNum;
	
}
