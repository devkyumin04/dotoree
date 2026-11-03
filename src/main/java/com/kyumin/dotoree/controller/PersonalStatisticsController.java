package com.kyumin.dotoree.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.StatisticsResponseDto;
import com.kyumin.dotoree.service.PersonalStatisticsService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "통계", description = "월별 수입 · 지출 · 잔액, 카테고리별 비율, 잔액 예측")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class PersonalStatisticsController {

	/*
	 * 개인 통계 API 는 하나. GET /api/statistics?year=&month=
	 * 요약(수입합·지출합·잔액·수입 대비 지출) + 지출 봉투 목록 + 수입 봉투 목록 + 6개월 추이를 한 응답에 담는다.
	 * 계산은 전부 서비스에서, 여기는 HTTP 입구 검증(month 1~12)과 응답 포장만.
	 */

	private final PersonalStatisticsService personalStatisticsService;

	@GetMapping
	public ResponseEntity<StatisticsResponseDto> getStatistics(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam int year,
			@RequestParam @Min(1) @Max(12) int month) {

		StatisticsResponseDto responseDto =
				personalStatisticsService.getStatistics(userNum, year, month);

		return ResponseEntity.ok(responseDto);
	}
}
