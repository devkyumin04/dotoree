package com.kyumin.dotoree.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;   // yml 의 15m → Duration (Spring Boot 가 변환)

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") Duration accessTokenExpiration) {
        // 글자 → 바이트는 인코딩을 못 박는다 — 기본값은 실행 환경(OS · JVM 설정)마다 달라서, 같은 시크릿이 서버마다 다른 키가 될 수 있다
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

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

    // 검증과 번호 꺼내기를 한 번의 파싱으로 — 서명 · 만료 · 형식이 틀린 토큰은 비어 있는 값을 돌려준다
    // 예전엔 validateToken 으로 한 번, getUserNumFromToken 으로 또 한 번 파싱했다(서명 검사가 요청마다 두 번)
    public Optional<Integer> parseUserNum(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Integer.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException — 서명 틀림 · 만료 · 깨진 형식. IllegalArgumentException — 빈 문자열, 번호가 아닌 subject(NumberFormatException 포함)
            // 그 밖의 예외(예: NullPointerException 같은 우리 버그)는 잡지 않고 터뜨린다 — 예전 catch (Exception) 은 그것까지 "토큰이 틀림"으로 삼켰다
            return Optional.empty();
        }
    }
}
