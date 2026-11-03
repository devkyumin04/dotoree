package com.kyumin.dotoree.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

    	String header = request.getHeader("Authorization");

    	String token = null;
    	if (header != null && header.startsWith("Bearer ")) {
    	    token = header.substring(7);   // "Bearer " 7글자를 잘라내고 순수 토큰만 남김
    	}
    	if (token != null) {
    	    // 검증과 번호 꺼내기가 한 번의 파싱 — 틀린 토큰이면 비어 있어 인증 없이 다음으로 간다
    	    jwtTokenProvider.parseUserNum(token).ifPresent(userNum -> {
    	        UsernamePasswordAuthenticationToken authentication =
    	                new UsernamePasswordAuthenticationToken(userNum, null, java.util.Collections.emptyList());
    	        SecurityContextHolder.getContext().setAuthentication(authentication);
    	    });
    	}
        // 토큰이 없거나 틀려도 그냥 다음으로 — 인증이 필요한 주소를 막는 건 SecurityConfig
        filterChain.doFilter(request, response);
    }
}