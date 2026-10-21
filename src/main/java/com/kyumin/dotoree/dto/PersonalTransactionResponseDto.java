package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 응답 DTO지만 불변(@AllArgsConstructor)이 아닌 이유:
// findByMonth 가 MyBatis resultType 으로 이 클래스를 직접 채운다 -> 기본생성자 + setter 필요 (ADR-038 예외)
@Getter
@Setter
@NoArgsConstructor
public class PersonalTransactionResponseDto {
	private Integer transNum;
	private Integer version;
	private Long transAmount;
	private LocalDate transDate;
	private String transMemo;
	private String transType;
	
	private String categoryName;
	private String categoryEmoji;
	
	private Integer categoryNum;
	
	private Integer accountNum;
	private String accountName;
}
