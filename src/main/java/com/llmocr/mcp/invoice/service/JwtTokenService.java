package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token Service for local authentication (Admin UI users)
 * 
 * This is separate from the MCP introspection-based authentication
 */
@Service
@Slf4j
public class JwtTokenService {

    @Value("${jwt.secret:your-256-bit-secret-key-change-this-in-production-minimum-32-chars}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long expiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days in milliseconds
    private Long refreshExpiration;

    /**
     * Generate JWT access token for a user
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("tenantId", user.getTenantId());
        claims.put("role", user.getRole().name());
        
        return createToken(claims, user.getUsername(), expiration);
    }

    /**
     * Generate refresh token
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", "refresh");
        
        return createToken(claims, user.getUsername(), refreshExpiration);
    }

    /**
     * Create JWT token with claims
     */
    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extract user ID from token
     */
    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }

    /**
     * Extract tenant ID from token
     */
    public String extractTenantId(String token) {
        return extractClaims(token).get("tenantId", String.class);
    }

    /**
     * Extract all claims from token
     */
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            return (extractedUsername.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get signing key from secret
     */
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

