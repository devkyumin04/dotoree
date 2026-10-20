package com.kyumin.dotoree.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.AccountRequestDto;
import com.kyumin.dotoree.dto.AccountResponseDto;
import com.kyumin.dotoree.service.AccountService;

import jakarta.validation.Valid;
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

	@PutMapping("/{accountNum}")
	public ResponseEntity<AccountResponseDto> updateAccount(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer accountNum,
			@Valid @RequestBody AccountRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.OK).body(accountService.updateAccount(userNum, accountNum, requestDto));
	}

	@DeleteMapping("/{accountNum}")
	public ResponseEntity<Void> closeAccount(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer accountNum) {
		accountService.closeAccount(userNum, accountNum);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
