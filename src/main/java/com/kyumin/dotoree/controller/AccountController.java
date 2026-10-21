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

import com.kyumin.dotoree.dto.AccountRequestDto;
import com.kyumin.dotoree.dto.AccountResponseDto;
import com.kyumin.dotoree.dto.BalanceSummaryResponseDto;
import com.kyumin.dotoree.dto.MonthlyBalanceDto;
import com.kyumin.dotoree.service.AccountService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// 계좌 — 등록 · 목록(잔액 포함) · 수정 · 닫기 (ADR-065)
// 닫기를 DELETE 로 받는 이유 — 화면에선 "계좌 지우기"지만 서버는 기록을 남기고 숨긴다(카테고리 · 거래의 use_yn 과 같은 모양)
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;

	@PostMapping
	public ResponseEntity<AccountResponseDto> createAccount(
			@AuthenticationPrincipal Integer userNum,
			@Valid @RequestBody AccountRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(userNum, requestDto));
	}

	@GetMapping
	public ResponseEntity<List<AccountResponseDto>> getAccounts(@AuthenticationPrincipal Integer userNum) {
		return ResponseEntity.status(HttpStatus.OK).body(accountService.getAccounts(userNum));
	}

	// 그날 기준 전체 잔액 합계 + 전날 대비 — ?date=2026-10-21, 비우면 오늘
	@GetMapping("/summary")
	public ResponseEntity<BalanceSummaryResponseDto> getSummary(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.status(HttpStatus.OK).body(accountService.getSummary(userNum, date));
	}

	// 월말 전체 잔액 합계 6개월 — ?year=2026&month=10 이 마지막 달
	@GetMapping("/monthly")
	public ResponseEntity<List<MonthlyBalanceDto>> getMonthlyBalances(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam int year,
			@RequestParam @Min(1) @Max(12) int month) {
		return ResponseEntity.status(HttpStatus.OK).body(accountService.getMonthlyBalances(userNum, year, month));
	}

	@PutMapping("/{accountNum}")
	public ResponseEntity<AccountResponseDto> updateAccount(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer accountNum,
			@Valid @RequestBody AccountRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.OK).body(accountService.updateAccount(userNum, accountNum, requestDto));
	}

	// ?endRecurring=true — 이 계좌를 쓰는 반복도 같이 끝낸다(없으면 무시)
	@DeleteMapping("/{accountNum}")
	public ResponseEntity<Void> closeAccount(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer accountNum,
			@RequestParam(defaultValue = "false") boolean endRecurring) {
		accountService.closeAccount(userNum, accountNum, endRecurring);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
