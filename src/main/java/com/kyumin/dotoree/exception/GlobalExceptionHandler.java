package com.kyumin.dotoree.exception;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    // 응답 본문은 전부 ProblemDetail(RFC 9457) — {type, title, status, detail, instance}. 사람에게 보여 줄 문구는 detail (ADR-061)
    // ProblemDetail 을 돌려주면 스프링이 그 status 로 응답하고 instance(요청 경로)를 채운다

    // ── 도메인 예외 — 전부 BusinessException 하나로 받는다 (ADR-062). 상태 코드·문구는 ErrorCode 한 줄에 있다
    //    새 에러가 생겨도 ErrorCode 에 한 줄 — 여기는 안 는다

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e, HttpServletRequest request) {
        // 4xx 는 요청 쪽 잘못이라 스택 없이 warn 한 줄 — 왜(ErrorCode 이름) · 무엇을(메서드·경로) · 누가(userNum, 비로그인은 -) (ADR-063)
        // 이메일·비번 같은 요청 값은 안 남긴다. 스택까지 남기는 건 아래 500 하나
        warn4xx(e.getErrorCode().name(), request, "");
        return ProblemDetail.forStatusAndDetail(e.getErrorCode().getStatus(), e.getMessage());
    }

    // ── 스프링 예외 — 문구가 각각 달라 하나씩 ─────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        // 틀린 칸 이름만 남긴다 — e.getMessage() 와 거절된 값(rejectedValue)엔 사용자가 보낸 값이 들어 있어 비번 칸이면 비번이 찍힌다 (ADR-063)
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .toList();
        warn4xx("VALIDATION_FAILED", request, " fields=" + fields);

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        warn4xx("UNREADABLE_BODY", request, "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        warn4xx("MISSING_PARAM", request, " param=" + e.getParameterName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "필수 요청 값이 누락되었습니다: " + e.getParameterName());
    }

    // @RequestParam 에 건 @Min/@Max 위반 (Spring 6.1+ 내장 메서드 검증. 컨트롤러에 @Validated 없어야 이 예외로 온다)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        warn4xx("METHOD_VALIDATION_FAILED", request, "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 허용 범위를 벗어났습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        warn4xx("TYPE_MISMATCH", request, " param=" + e.getName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        // 스택은 로그로만. 응답엔 고정 문구 (DB 스키마 등 내부 정보 노출 방지, ADR-012)
        log.error("처리되지 않은 예외", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    // 4xx 로그 한 줄의 공통 모양 — 왜 · 무엇을 · 어디에 · 누가(userNum, 비로그인은 -) + 덧붙일 것. 요청 값은 넣지 않는다 (ADR-063)
    private void warn4xx(String code, HttpServletRequest request, String extra) {
        Principal user = request.getUserPrincipal();
        log.warn("{} {} {} user={}{}", code, request.getMethod(), request.getRequestURI(),
                user == null ? "-" : user.getName(), extra);
    }
}