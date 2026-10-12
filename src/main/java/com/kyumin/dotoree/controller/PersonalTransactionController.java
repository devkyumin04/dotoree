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

	/*
	 * ============================================================
	 *  컨트롤러의 역할 : HTTP 입출력만.
	 *    - 요청에서 값을 꺼내 서비스에 넘긴다
	 *    - 서비스 결과에 상태코드를 붙여 반환한다
	 *    - 판단(검증·분기)은 하지 않는다. 그건 서비스 몫
	 *
	 *  카테고리 컨트롤러와 달라지는 점 2개
	 *    ① @RequestParam 이 처음 등장한다 (year/month, version)
	 *    ② 낙관적 락 때문에 수정·삭제가 version 을 쿼리파라미터로 받는다 (ADR-028)
	 * ============================================================
	 */
	private final PersonalTransactionService personalTransactionService;


	/* ── 등록  POST /api/transactions ──────────────────────────────
	 *
	 *  파라미터
	 *    @AuthenticationPrincipal Integer userNum   JWT 에서 꺼낸 사용자
	 *    @Valid @RequestBody      RequestDto        본문 JSON
	 *                                               @Valid 가 DTO 의 검증 애노테이션을
	 *                                               실제로 작동시킨다 (금액/날짜/메모)
	 *
	 *  서비스 호출 → createTransaction(userNum, requestDto)
	 *
	 *  상태코드 : 201 CREATED  (새 자원을 만들었으므로)
	 * ───────────────────────────────────────────────────────────── */
	@PostMapping
	public ResponseEntity<PersonalTransactionResponseDto> createTransaction(
		@AuthenticationPrincipal Integer userNum, 
		@Valid @RequestBody PersonalTransactionRequestDto requestDto
	){
		PersonalTransactionResponseDto responseDto = personalTransactionService.createTransaction(userNum, requestDto);
		return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
	}


	/* ── 월 목록 조회  GET /api/transactions?year=2026&month=9 ─────
	 *
	 *  파라미터
	 *    @AuthenticationPrincipal Integer userNum
	 *    @RequestParam int year      ← ?year=2026
	 *    @RequestParam int month     ← ?month=9
	 *
	 *    @RequestParam 은 기본이 required=true 라서
	 *    빠뜨리면 스프링이 알아서 400 을 낸다.
	 *    month 는 @Min/@Max 로 1~12 입구 검증 (통계 API 와 같은 구조. 없으면 LocalDate.of 가 500)
	 *
	 *  반환 타입은 List 라서 제네릭에 List<...> 를 그대로 넣는다
	 *
	 *  상태코드 : 200 OK
	 * ───────────────────────────────────────────────────────────── */
	@GetMapping
	public ResponseEntity<List<PersonalTransactionResponseDto>> getTransactions(
		@AuthenticationPrincipal Integer userNum, 
		@RequestParam int year, 
		@RequestParam @Min(1) @Max(12) int month
	){
		List<PersonalTransactionResponseDto> responseDtoList = personalTransactionService.getTransactions(userNum, year, month);
		return ResponseEntity.status(HttpStatus.OK).body(responseDtoList);
	}
	


	/* ── 수정  PUT /api/transactions/{transNum}?version=3 ──────────
	 *
	 *  파라미터
	 *    @AuthenticationPrincipal Integer userNum
	 *    @PathVariable  Integer transNum    ← URL 경로의 {transNum}
	 *    @RequestParam  Integer version     ← ?version=3  (낙관적 락)
	 *    @Valid @RequestBody RequestDto
	 *
	 *    version 을 body 가 아니라 쿼리파라미터로 받는 이유는 ADR-028 참고.
	 *    (body 에 두면 등록 때는 의미 없는 필드가 되어 DTO 를 쪼개야 함)
	 *
	 *  서비스 호출 → updateTransaction(userNum, transNum, version, requestDto)
	 *
	 *  상태코드 : 200 OK
	 *    409 CONFLICT 는 여기서 직접 만들지 않는다.
	 *    서비스가 BusinessException(TRANSACTION_CONFLICT) 을 던지고
	 *    GlobalExceptionHandler 가 409 로 변환한다.
	 * ───────────────────────────────────────────────────────────── */
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


	/* ── 삭제  DELETE /api/transactions/{transNum}?version=3 ───────
	 *
	 *  파라미터
	 *    @AuthenticationPrincipal Integer userNum
	 *    @PathVariable Integer transNum
	 *    @RequestParam Integer version
	 *    (body 없음)
	 *
	 *  서비스 반환이 void 이므로 컨트롤러 반환 타입은 ResponseEntity<Void>
	 *
	 *  상태코드 : 204 NO_CONTENT  (성공했지만 돌려줄 내용 없음)
	 *             body 가 없으므로 .body(...) 대신 .build()
	 * ───────────────────────────────────────────────────────────── */
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
