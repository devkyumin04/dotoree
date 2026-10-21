package com.kyumin.dotoree.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.BalanceAdjustmentRequestDto;
import com.kyumin.dotoree.dto.BalanceAdjustmentResponseDto;
import com.kyumin.dotoree.service.BalanceAdjustmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// 잔액 맞추기 (ADR-066)
// 만들기는 계좌 아래(POST /api/accounts/{n}/adjustments — "이 계좌를 맞춘다"), 월 목록 · 지우기는 이체처럼 /api/adjustments
// 차이가 0 이면 만들 게 없어 204
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BalanceAdjustmentController {

	private final BalanceAdjustmentService adjustmentService;

	@PostMapping("/accounts/{accountNum}/adjustments")
	public ResponseEntity<BalanceAdjustmentResponseDto> adjust(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer accountNum,
			@Valid @RequestBody BalanceAdjustmentRequestDto requestDto) {
		BalanceAdjustmentResponseDto created = adjustmentService.adjust(userNum, accountNum, requestDto.getActualBalance());
		if (created == null) {
			return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/adjustments")
	public ResponseEntity<List<BalanceAdjustmentResponseDto>> getAdjustments(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam int year,
			@RequestParam @Min(1) @Max(12) int month) {
		return ResponseEntity.status(HttpStatus.OK).body(adjustmentService.getAdjustments(userNum, year, month));
	}

	@DeleteMapping("/adjustments/{adjustmentNum}")
	public ResponseEntity<Void> deleteAdjustment(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer adjustmentNum) {
		adjustmentService.deleteAdjustment(userNum, adjustmentNum);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
