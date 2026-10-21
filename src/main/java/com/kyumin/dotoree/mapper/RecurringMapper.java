package com.kyumin.dotoree.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.RecurringOccurrence;
import com.kyumin.dotoree.domain.RecurringRule;
import com.kyumin.dotoree.dto.RecurringRuleResponseDto;

// 반복 규칙 · 처리한 예정 (ADR-067). 예정일 계산은 SQL 이 아니라 RecurringRule 한 곳
@Mapper
public interface RecurringMapper {

	void insertRule(RecurringRule rule);

	RecurringRule findRule(@Param("ruleNum") Integer ruleNum);

	// 살아 있는 규칙 + 이름들 — 목록 · 남은 예정 · 예상 잔액이 같이 쓴다
	List<RecurringRuleResponseDto> findRules(@Param("userNum") Integer userNum);

	int updateRule(RecurringRule rule);

	int deleteRule(@Param("ruleNum") Integer ruleNum);

	// 계좌를 닫을 때 — 그 계좌를 쓰는 살아 있는 규칙 수 / 같이 끝내기
	int countRulesUsingAccount(@Param("accountNum") Integer accountNum);

	int deleteRulesUsingAccount(@Param("accountNum") Integer accountNum);

	// 카테고리를 지울 때 — 거래처럼 '미분류'로 옮긴다
	int moveToDefaultCategory(@Param("categoryNum") Integer categoryNum, @Param("defaultCategoryNum") Integer defaultCategoryNum);

	void insertOccurrence(RecurringOccurrence occurrence);

	// 처리한 예정 — "규칙번호:날짜" 로 남은 예정에서 뺀다
	List<RecurringOccurrence> findOccurrences(@Param("userNum") Integer userNum);
}
