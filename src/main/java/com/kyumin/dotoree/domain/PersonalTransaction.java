package com.kyumin.dotoree.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonalTransaction {
	private Integer transNum;
	private Integer userNum;
	private Integer categoryNum;
	private Integer accountNum;
	private String transType;
	private Long transAmount;
	private LocalDate transDate;
	private String transMemo;
	private String placeName;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private String captureImgUrl;
	private String aiAnalyzedYn;
	private Integer version;
	private String useYn;
	private LocalDateTime createdAt;	
}
