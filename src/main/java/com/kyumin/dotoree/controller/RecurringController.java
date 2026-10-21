package com.kyumin.dotoree.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.ForecastResponseDto;
import com.kyumin.dotoree.dto.OccurrenceRequestDto;
import com.kyumin.dotoree.dto.OccurrenceResponseDto;
import com.kyumin.dotoree.dto.PendingOccurrenceDto;
import com.kyumin.dotoree.dto.RecurringRuleRequestDto;
import com.kyumin.dotoree.dto.RecurringRuleResponseDto;
import com.kyumin.dotoree.service.RecurringService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 반복 예정 (욕조 ② — ADR-067)
// 규칙 CRUD · 남은 예정 · 확인(거래 · 이체가 생김) · 건너뛰기 · 예상 잔액. until 을 비우면 다음 달 말
@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

	private final RecurringService recurringService;

	@PostMapping
	public ResponseEntity<RecurringRuleResponseDto> createRule(
			@AuthenticationPrincipal Integer userNum,
			@Valid @RequestBody RecurringRuleRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.createRule(userNum, requestDto));
	}

	@GetMapping
	public ResponseEntity<List<RecurringRuleResponseDto>> getRules(@AuthenticationPrincipal Integer userNum) {
		return ResponseEntity.status(HttpStatus.OK).body(recurringService.getRules(userNum));
	}

	@PutMapping("/{ruleNum}")
	public ResponseEntity<RecurringRuleResponseDto> updateRule(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer ruleNum,
			@Valid @RequestBody RecurringRuleRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.OK).body(recurringService.updateRule(userNum, ruleNum, requestDto));
	}

	@DeleteMapping("/{ruleNum}")
	public ResponseEntity<Void> deleteRule(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer ruleNum) {
		recurringService.deleteRule(userNum, ruleNum);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@GetMapping("/pending")
	public ResponseEntity<List<PendingOccurrenceDto>> getPending(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
		return ResponseEntity.status(HttpStatus.OK).body(recurringService.getPending(userNum, until));
	}

	@PostMapping("/{ruleNum}/confirm")
	public ResponseEntity<OccurrenceResponseDto> confirm(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer ruleNum,
			@Valid @RequestBody OccurrenceRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.confirm(userNum, ruleNum, requestDto));
	}

	@PostMapping("/{ruleNum}/skip")
	public ResponseEntity<OccurrenceResponseDto> skip(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer ruleNum,
			@Valid @RequestBody OccurrenceRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.skip(userNum, ruleNum, requestDto));
	}

	@GetMapping("/forecast")
	public ResponseEntity<ForecastResponseDto> forecast(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
		return ResponseEntity.status(HttpStatus.OK).body(recurringService.forecast(userNum, until));
	}
}
