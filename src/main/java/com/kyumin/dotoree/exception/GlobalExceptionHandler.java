package com.kyumin.dotoree.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    // ── 도메인 예외 — 의미별 부모 4개로 받는다 (ADR-041, 2026-09-30). 자식(CategoryNotFound …)은 부모를 상속만 하고
    //    핸들러를 갖지 않는다 → 새 예외가 생겨도 여기는 안 는다. 자식 클래스명은 유지 — 로그에서 출처 식별 (ADR-035)

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> handleBadRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // 401 은 자식이 하나뿐이라 부모 없이 그대로 (YAGNI)
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    // 423 — 로그인 잠금 (ADR-057). 상태가 하나뿐이라 부모 없음
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<String> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(e.getMessage());
    }

    // ── 스프링 예외 — 문구가 각각 달라 하나씩 ─────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("필수 요청 값이 누락되었습니다: " + e.getParameterName());
    }

    // @RequestParam 에 건 @Min/@Max 위반 (Spring 6.1+ 내장 메서드 검증. 컨트롤러에 @Validated 없어야 이 예외로 온다)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<String> handleMethodValidation(HandlerMethodValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("요청 값이 허용 범위를 벗어났습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("요청 값의 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e) {
        // 스택은 로그로만. 응답엔 고정 문구 (DB 스키마 등 내부 정보 노출 방지, ADR-012)
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류가 발생했습니다.");
    }
}