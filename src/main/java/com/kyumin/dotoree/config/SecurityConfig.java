package com.kyumin.dotoree.config;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.kyumin.dotoree.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	
	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
	
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // 보안 헤더 — HSTS·X-Frame-Options·nosniff 는 Spring Security 기본값이 이미 붙인다(2026-09-21 실측). 여기선 CSP 만 얹는다
            // CSP = XSS 두 번째 방어선. escapeHtml 을 한 곳 빠뜨려도 브라우저가 HTML 에 박힌 코드(onerror= 등)를 실행하지 않게
            .headers(headers -> headers
                // 키워드는 작은따옴표까지가 값("'self'", "'none'"). 외부 출처는 0개 — 기능이 붙을 때(카카오맵 등) 지시어를 한 줄씩 연다
                // 금지: 'unsafe-inline' · 'unsafe-eval' · *  — 넣는 순간 CSP 가 XSS 를 거의 못 막는다
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src "     + "'self'" + "; " +   // 따로 안 적은 자원(script·css·fetch·img·font)의 기본값 — 우리 서버만
                    "object-src "      + "'none'" + "; " +   // <object>·플러그인 — 전부 금지
                    "base-uri "        + "'none'" + "; " +   // <base> 태그 바꿔치기 — 안 쓰므로 전부 금지
                    "frame-ancestors " + "'none'" + "; " +   // 남이 우리를 iframe 에 넣기 — 전부 금지 (X-Frame-Options 의 최신판)
                    "form-action "     + "'self'"            // <form> 전송처 — 우리 서버만
                ))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                // 화면(html·css·js)은 전부 permitAll 이라, API 가 아닌데 여기까지 왔다면 없는 주소다 → 404 페이지 (ADR-012 의 API/페이지 분리)
                // sendError 는 /error 로 넘어가 CustomErrorController 가 404.html 을 보여준다. /error 는 /error/** 로 이미 permitAll
                if (!request.getRequestURI().startsWith("/api/")) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("로그인이 필요합니다.");
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/users/signup", "/api/users/login").permitAll()
                .requestMatchers("/api/auth/**").permitAll()   // 재발급·로그아웃 — Access 가 만료된 뒤에 부르므로 열어 둔다. 쿠키 Path=/api/auth 와 같은 범위
                .requestMatchers("/", "/views/**", "/css/**", "/js/**", "/error/**").permitAll()
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    
}
