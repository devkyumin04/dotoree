package com.kyumin.dotoree.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;   // yml 의 15m → Duration (Spring Boot 가 변환)

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") Duration accessTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
    }

    // 1. 토큰 생성
    public String createAccessToken(Integer userNum) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration.toMillis());

        return Jwts.builder()
                .subject(String.valueOf(userNum))
                .issuedAt(now)                   
                .expiration(expiry)              
                .signWith(secretKey)             
                .compact();                      
    }

    // 2. 토큰에서 userNum 꺼내기 (나중에 필터에서 씀)
    public Integer getUserNumFromToken(String token) {
    	String subject = Jwts.parser()
    	        .verifyWith(secretKey)
    	        .build()
    	        .parseSignedClaims(token)
    	        .getPayload()
    	        .getSubject();

    	return Integer.valueOf(subject);
    }

    // 3. 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}