package com.kyumin.dotoree.dto;

import java.time.LocalDate;

import com.kyumin.dotoree.domain.RecurringRule;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 규칙 + 화면에 보일 이름들. 목록은 MyBatis resultType 으로 직접 채운다 → 기본 생성자 + setter (ADR-038)
// kind — I 수입 · E 지출(카테고리에서) · T 이체
@Getter
@Setter
@NoArgsConstructor
public class RecurringRuleResponseDto {

	private Integer ruleNum;
	private String kind;
	private Integer categoryNum;
	private String categoryName;
	private String categoryEmoji;
	private Integer accountNum;
	private String accountName;
	private Integer fromAccountNum;
	private String fromAccountName;
	private Integer toAccountNum;
	private String toAccountName;
	private Long ruleAmount;
	private String ruleMemo;
	private String repeatType;
	private Integer repeatMonth;
	private Integer repeatDay;
	private LocalDate startDate;
	private LocalDate endDate;

	// 날짜 계산은 규칙(도메인) 한 곳에 맡긴다
	public RecurringRule toRule() {
		RecurringRule rule = new RecurringRule();
		rule.setRuleNum(ruleNum);
		rule.setCategoryNum(categoryNum);
		rule.setAccountNum(accountNum);
		rule.setFromAccountNum(fromAccountNum);
		rule.setToAccountNum(toAccountNum);
		rule.setRuleAmount(ruleAmount);
		rule.setRepeatType(repeatType);
		rule.setRepeatMonth(repeatMonth);
		rule.setRepeatDay(repeatDay);
		rule.setStartDate(startDate);
		rule.setEndDate(endDate);
		return rule;
	}
}
