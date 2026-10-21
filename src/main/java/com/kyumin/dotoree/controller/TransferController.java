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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.TransferRequestDto;
import com.kyumin.dotoree.dto.TransferResponseDto;
import com.kyumin.dotoree.service.TransferService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// 이체 — 거래 API 와 같은 모양(월 목록 · version 은 쿼리 파라미터, ADR-028) (ADR-065)
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferService transferService;

	@PostMapping
	public ResponseEntity<TransferResponseDto> createTransfer(
			@AuthenticationPrincipal Integer userNum,
			@Valid @RequestBody TransferRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(transferService.createTransfer(userNum, requestDto));
	}

	@GetMapping
	public ResponseEntity<List<TransferResponseDto>> getTransfers(
			@AuthenticationPrincipal Integer userNum,
			@RequestParam int year,
			@RequestParam @Min(1) @Max(12) int month) {
		return ResponseEntity.status(HttpStatus.OK).body(transferService.getTransfers(userNum, year, month));
	}

	@PutMapping("/{transferNum}")
	public ResponseEntity<TransferResponseDto> updateTransfer(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer transferNum,
			@RequestParam Integer version,
			@Valid @RequestBody TransferRequestDto requestDto) {
		return ResponseEntity.status(HttpStatus.OK).body(transferService.updateTransfer(userNum, transferNum, version, requestDto));
	}

	@DeleteMapping("/{transferNum}")
	public ResponseEntity<Void> deleteTransfer(
			@AuthenticationPrincipal Integer userNum,
			@PathVariable Integer transferNum,
			@RequestParam Integer version) {
		transferService.deleteTransfer(userNum, transferNum, version);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
