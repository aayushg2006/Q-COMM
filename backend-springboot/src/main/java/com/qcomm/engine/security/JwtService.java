package com.qcomm.engine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${engine.security.jwt.secret:qcomm-engine-jwt-secret-key-at-least-32-bytes}") String secret,
            @Value("${engine.security.jwt.expiration-seconds:36000}") long expirationSeconds
    ) {
        this.signingKey = resolveKey(secret);
        this.expirationSeconds = Math.max(300L, expirationSeconds);
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role == null ? "USER" : role.toString();
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        Claims claims = parseClaims(token);
        String subject = claims.getSubject();
        Date expiration = claims.getExpiration();
        return expectedUsername.equals(subject) && expiration != null && expiration.after(new Date());
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private SecretKey resolveKey(String configuredSecret) {
        String candidate = configuredSecret == null ? "" : configuredSecret.trim();
        if (candidate.isBlank()) {
            candidate = "qcomm-engine-jwt-secret-key-at-least-32-bytes";
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(candidate);
            if (keyBytes.length < 32) {
                keyBytes = candidate.getBytes(StandardCharsets.UTF_8);
            }
        }
        catch (Exception ex) {
            keyBytes = candidate.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            for (int i = 0; i < padded.length; i++) {
                padded[i] = keyBytes[i % keyBytes.length];
            }
            keyBytes = padded;
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
