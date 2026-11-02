package com.kyumin.dotoree.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.dto.PersonalTransactionRequestDto;
import com.kyumin.dotoree.dto.PersonalTransactionResponseDto;
import com.kyumin.dotoree.service.PersonalTransactionService;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class PersonalTransactionController {

	// 컨트롤러는 HTTP 입출력만 — 판단(검증 · 분기)은 서비스
	private final PersonalTransactionService personalTransactionService;


	@PostMapping
	public ResponseEntity<PersonalTransactionResponseDto> createTransaction(
		@AuthenticationPrincipal Integer userNum, 
		@Valid @RequestBody PersonalTransactionRequestDto requestDto
	){
		PersonalTransactionResponseDto responseDto = personalTransactionService.createTransaction(userNum, requestDto);
		return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
	}


	// month 는 @Min/@Max 로 1~12 입구 검증 — 없으면 LocalDate.of 가 500 (통계 API 와 같은 구조)
	@GetMapping
	public ResponseEntity<List<PersonalTransactionResponseDto>> getTransactions(
		@AuthenticationPrincipal Integer userNum, 
		@RequestParam int year, 
		@RequestParam @Min(1) @Max(12) int month
	){
		List<PersonalTransactionResponseDto> responseDtoList = personalTransactionService.getTransactions(userNum, year, month);
		return ResponseEntity.status(HttpStatus.OK).body(responseDtoList);
	}
	


	// version 을 body 가 아니라 쿼리파라미터로 받는 이유는 ADR-028 (body 에 두면 등록 때는 의미 없는 필드가 되어 DTO 를 쪼개야 함)
	// 409 는 여기서 만들지 않는다 — 서비스가 BusinessException(TRANSACTION_CONFLICT) 을 던지면 GlobalExceptionHandler 가 변환
	@PutMapping("/{transNum}")
	public ResponseEntity<PersonalTransactionResponseDto> updateTransaction(
		@AuthenticationPrincipal Integer userNum, 
		@PathVariable Integer transNum, 
		@RequestParam Integer version, 
		@Valid @RequestBody PersonalTransactionRequestDto requestDto
	){
		PersonalTransactionResponseDto responseDto = personalTransactionService.updateTransaction(userNum, transNum, version, requestDto);
		return ResponseEntity.status(HttpStatus.OK).body(responseDto);
	}


	// 삭제도 version 을 받는다 (ADR-028)
	@DeleteMapping("/{transNum}")
	public ResponseEntity<Void> deleteTransaction(
		@AuthenticationPrincipal Integer userNum, 
		@PathVariable Integer transNum, 
		@RequestParam Integer version
	){
		personalTransactionService.deleteTransaction(userNum, transNum, version);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

}
