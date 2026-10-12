package com.kyumin.dotoree.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute("jakarta.servlet.error.status_code");
        Object requestUri = request.getAttribute("jakarta.servlet.error.request_uri");

        int code = statusCode != null ? Integer.parseInt(statusCode.toString()) : 500;
        String uri = requestUri != null ? requestUri.toString() : "";

        // API 요청은 HTML 대신 상태코드 + ProblemDetail (ADR-061)
        if (uri.startsWith("/api/")) {
            HttpStatus status = HttpStatus.resolve(code);
            String message = (status != null && status.is4xxClientError())
                    ? "요청을 처리할 수 없습니다. (" + code + ")"
                    : "서버 오류가 발생했습니다.";
            return ResponseEntity.status(code).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code), message));
        }

        // 페이지 요청은 HTML
        if (code >= 500) {
            return new ModelAndView("forward:/error/500.html");
        }
        return new ModelAndView("forward:/error/404.html");
    }
}
