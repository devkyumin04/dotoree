package com.kyumin.dotoree.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.kyumin.dotoree.config.DemoAccount;
import com.kyumin.dotoree.domain.User;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.UserMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 데모 계정의 쓰기 요청을 막는다 — 읽기(GET · HEAD · OPTIONS)는 통과
// 서버에서 막는 이유: 화면 버튼을 숨기는 건 막는 게 아니다(API 를 직접 부를 수 있다). 계산 · 검사는 서버 몫이라는 원칙과 같다
// /api/auth/** 는 열어 둔다 — 재발급 · 로그아웃이 쓰기(POST)지만 데모 사용자도 써야 한다
// JwtAuthenticationFilter 뒤에서 돈다(SecurityConfig) — 로그인한 사용자 번호가 있어야 데모인지 알 수 있다
@Component
@RequiredArgsConstructor
public class DemoGuardFilter extends OncePerRequestFilter {

	private final DemoAccount demoAccount;
	private final UserMapper userMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (demoAccount.isEnabled() && isGuardedWrite(request) && isDemoUser()) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			// 필터는 컨트롤러 밖이라 GlobalExceptionHandler 가 못 받는다 → 같은 ProblemDetail 모양을 직접 쓴다 (SecurityConfig 의 401 과 같은 이유)
			response.setContentType("application/problem+json;charset=UTF-8");
			response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\""
					+ ErrorCode.DEMO_READ_ONLY.getMessage() + "\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean isGuardedWrite(HttpServletRequest request) {
		String method = request.getMethod();
		boolean read = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
		String path = request.getRequestURI();
		return !read && path.startsWith("/api/") && !path.startsWith("/api/auth/");
	}

	private boolean isDemoUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Integer userNum)) {
			return false;
		}
		User user = userMapper.findByUserNum(userNum);
		return user != null && demoAccount.isDemo(user.getUserEmail());
	}
}
