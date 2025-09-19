package com.llmocr.mcp.invoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.AuthorizedClient;
import com.llmocr.mcp.invoice.repository.AuthorizedClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT Introspection Service for MCP Server
 * 
 * Validates JWTs by calling back to the main application's introspection endpoint.
 * This approach avoids sharing JWT signing secrets between applications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtIntrospectionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AuthorizedClientRepository authorizedClientRepository;

    // All configuration now database-driven via MCP server frontend
    @Value("${server.servlet.context-path:/mcp-invoice}")
    private String contextPath;
    
    @Value("${server.port:8081}")
    private int serverPort;

    /**
     * Validate a JWT token using the main app's introspection endpoint
     */
    public TokenValidationResult validateToken(String token) {
        try {
            // Prepare request
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("token", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // Use client's introspection endpoint from database
            // For now, use a default endpoint - will be enhanced with per-client lookup
            String defaultIntrospectionEndpoint = "http://localhost:8080/api/jwt/validate";
            
            // Call introspection endpoint
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    defaultIntrospectionEndpoint, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Boolean active = (Boolean) body.get("active");

                log.debug("Introspection response: {}", body);

                if (Boolean.TRUE.equals(active)) {
                    String userId = (String) body.get("sub");
                    String email = (String) body.get("email");
                    String tenantId = (String) body.get("tenant_id");
                    String issuer = (String) body.get("iss");
                    
                    log.debug("Extracted from token - userId: {}, email: {}, tenantId: {}, issuer: {}", 
                             userId, email, tenantId, issuer);
                    
                    // Validate audience and client access first to get client info
                    String audience = (String) body.get("aud");
                    String clientId = (String) body.get("client_id");
                    
                    // Get client-specific configuration from database
                    AuthorizedClient authorizedClient = getAuthorizedClient(tenantId, clientId);
                    if (authorizedClient == null) {
                        log.warn("Client '{}' not registered for tenant '{}' - access denied", clientId, tenantId);
                        return TokenValidationResult.invalid("Client not registered for this tenant");
                    }
                    
                    // Validate issuer against client-specific trusted issuer
                    if (!issuer.equals(authorizedClient.getTrustedIssuer())) {
                        log.warn("Token from untrusted issuer for client '{}'. Expected: {}, got: {}", 
                                clientId, authorizedClient.getTrustedIssuer(), issuer);
                        return TokenValidationResult.invalid("Token from untrusted issuer for this client");
                    }
                    
                    // Validate audience and final client access
                    if (!validateClientAccess(audience, clientId, tenantId)) {
                        return TokenValidationResult.invalid("Client not authorized for this server");
                    }
                    
                    return TokenValidationResult.builder()
                            .valid(true)
                            .userId(userId)
                            .email(email)
                            .tenantId(tenantId)
                            .expiration(((Number) body.get("exp")).longValue())
                            .issuedAt(((Number) body.get("iat")).longValue())
                            .build();
                } else {
                    String error = (String) body.get("error");
                    log.debug("Token validation failed: {}", error);
                    return TokenValidationResult.invalid("Token validation failed: " + error);
                }
            } else {
                log.warn("Introspection endpoint returned non-success status: {}", response.getStatusCode());
                return TokenValidationResult.invalid("Introspection endpoint error");
            }

        } catch (Exception e) {
            log.error("Token introspection failed", e);
            return TokenValidationResult.invalid("Introspection service unavailable");
        }
    }

    /**
     * Result of token validation
     */
    @lombok.Builder
    @lombok.Data
    public static class TokenValidationResult {
        private boolean valid;
        private String userId;
        private String email;
        private String tenantId;
        private Long expiration;
        private Long issuedAt;
        private String error;

        public static TokenValidationResult invalid(String error) {
            return TokenValidationResult.builder()
                    .valid(false)
                    .error(error)
                    .build();
        }
    }
    
    /**
     * Validate client access based on audience, client ID, and tenant-specific authorization
     */
    private boolean validateClientAccess(String audience, String clientId, String tenantId) {
        // Check audience claim (OAuth 2.1 compliance)
        // Audience must match this server's URL for proper security
        String expectedAudience = "http://localhost:" + serverPort + contextPath;
        
        if (audience == null || !audience.equals(expectedAudience)) {
            log.warn("SECURITY: Token audience mismatch. Expected: {}, got: {}", expectedAudience, audience);
            return false;
        }
        
        log.debug("SECURITY: Token audience validated: {}", audience);
        
        // CRITICAL: Check client authorization in database (tenant-specific)
        // NO automatic access - clients must be explicitly registered per tenant
        if (clientId == null || tenantId == null) {
            log.warn("SECURITY: Missing client_id or tenant_id in token - access denied");
            return false;
        }
        
        boolean isAuthorized = authorizedClientRepository
                .findByTenantIdAndClientIdAndIsActiveTrue(tenantId, clientId)
                .isPresent();
        
        if (!isAuthorized) {
            log.warn("SECURITY: Client '{}' NOT authorized for tenant '{}' on this MCP server - access denied", 
                    clientId, tenantId);
            return false;
        }
        
        log.debug("SECURITY: Client '{}' authorized for tenant '{}' on this MCP server", clientId, tenantId);
        
        return true;
    }
    
    /**
     * Get the authorized client configuration from the database
     */
    private AuthorizedClient getAuthorizedClient(String tenantId, String clientId) {
        if (tenantId == null || clientId == null) {
            return null;
        }
        
        return authorizedClientRepository
                .findByTenantIdAndClientIdAndIsActiveTrue(tenantId, clientId)
                .orElse(null);
    }
}
