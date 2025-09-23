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

            // Try to extract tenant and client info from token for database lookup
            // This enables per-tenant introspection endpoints while maintaining backward compatibility
            String tenantId = extractTenantIdFromToken(token);
            String clientId = extractClientIdFromToken(token);
            
            String introspectionEndpoint;
            AuthorizedClient authorizedClient = null;
            
            if (tenantId != null && clientId != null) {
                // New flow: Use client-specific introspection endpoint from database
                authorizedClient = getAuthorizedClient(tenantId, clientId);
                if (authorizedClient != null && authorizedClient.getIntrospectionEndpoint() != null && 
                    !authorizedClient.getIntrospectionEndpoint().trim().isEmpty()) {
                    introspectionEndpoint = authorizedClient.getIntrospectionEndpoint();
                    log.debug("Using client-specific introspection endpoint for client '{}' (tenant '{}'): {}", 
                             clientId, tenantId, introspectionEndpoint);
                } else {
                    // Fallback to default endpoint for backward compatibility
                    introspectionEndpoint = "http://localhost:8080/api/jwt/validate";
                    log.debug("Client '{}' for tenant '{}' not found or no endpoint configured, using default: {}", 
                             clientId, tenantId, introspectionEndpoint);
                }
            } else {
                // Legacy flow: Use default introspection endpoint for backward compatibility
                introspectionEndpoint = "http://localhost:8080/api/jwt/validate";
                log.debug("Token missing tenant_id or client_id claims, using default introspection endpoint: {}", 
                         introspectionEndpoint);
            }
            
            // Call introspection endpoint
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    introspectionEndpoint, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Boolean active = (Boolean) body.get("active");

                log.debug("Introspection response: {}", body);

                if (Boolean.TRUE.equals(active)) {
                    String userId = (String) body.get("sub");
                    String email = (String) body.get("email");
                    String issuer = (String) body.get("iss");
                    
                    log.debug("Extracted from token - userId: {}, email: {}, tenantId: {}, issuer: {}", 
                             userId, email, tenantId, issuer);
                    
                    // Validate audience and client access
                    String audience = (String) body.get("aud");
                    
                    // Validate issuer against client-specific trusted issuer (if we have client config)
                    if (authorizedClient != null) {
                        if (!issuer.equals(authorizedClient.getTrustedIssuer())) {
                            log.warn("Token from untrusted issuer for client '{}'. Expected: {}, got: {}", 
                                    clientId, authorizedClient.getTrustedIssuer(), issuer);
                            return TokenValidationResult.invalid("Token from untrusted issuer for this client");
                        }
                    } else {
                        log.debug("No client configuration found, skipping issuer validation for backward compatibility");
                    }
                    
                    // Validate audience and final client access (only if we have client info)
                    if (tenantId != null && clientId != null) {
                        if (!validateClientAccess(audience, clientId, tenantId)) {
                            return TokenValidationResult.invalid("Client not authorized for this server");
                        }
                    } else {
                        log.debug("Token missing tenant/client claims, skipping client access validation for backward compatibility");
                    }
                    
                    return TokenValidationResult.builder()
                            .valid(true)
                            .userId(userId)
                            .email(email)
                            .tenantId(tenantId)
                            .clientId(clientId)
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
        private String clientId;
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
        // CRITICAL: Check client authorization in database (tenant-specific)
        // NO automatic access - clients must be explicitly registered per tenant
        if (clientId == null || tenantId == null) {
            log.warn("SECURITY: Missing client_id or tenant_id in token - access denied");
            return false;
        }
        
        // Get authorized client from database
        var authorizedClientOpt = authorizedClientRepository
                .findByTenantIdAndClientIdAndIsActiveTrue(tenantId, clientId);
        
        if (authorizedClientOpt.isEmpty()) {
            log.warn("SECURITY: Client '{}' not authorized for tenant '{}' on this MCP server", clientId, tenantId);
            return false;
        }
        
        var authorizedClient = authorizedClientOpt.get();
        
        // Check audience claim (OAuth 2.1 compliance)
        // Audience must match the expected audience URL from the database
        String expectedAudience = authorizedClient.getAudienceUrl();
        
        // If no audience URL is configured in database, fall back to server URL
        if (expectedAudience == null || expectedAudience.trim().isEmpty()) {
            // Support both localhost and Docker service name for backward compatibility
            String localhostAudience = "http://localhost:" + serverPort + contextPath;
            String dockerAudience = "http://mcp-invoice-server:" + serverPort + contextPath;
            
            boolean audienceValid = audience != null && 
                    (audience.equals(localhostAudience) || audience.equals(dockerAudience));
            
            if (!audienceValid) {
                log.warn("SECURITY: Token audience mismatch (fallback validation). Expected: {} or {}, got: {}", 
                        localhostAudience, dockerAudience, audience);
                return false;
            }
            
            log.debug("SECURITY: Token audience validated using fallback: {}", audience);
        } else {
            // Use configured audience URL from database
            if (audience == null || !audience.equals(expectedAudience)) {
                log.warn("SECURITY: Token audience mismatch. Expected: {}, got: {}", expectedAudience, audience);
                return false;
            }
            
            log.debug("SECURITY: Token audience validated against database configuration: {}", audience);
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
    
    /**
     * Extract tenant_id from JWT token without validation
     * This is used to determine which introspection endpoint to use
     * Handles both "tenantId" (camelCase) and "tenant_id" (snake_case) for backward compatibility
     */
    private String extractTenantIdFromToken(String token) {
        try {
            // Decode JWT payload without verification (we'll validate via introspection)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String payload = parts[1];
            // Add padding if needed
            while (payload.length() % 4 != 0) {
                payload += "=";
            }
            
            byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(payload);
            String payloadJson = new String(decodedBytes);
            
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
            
            // Try both formats for backward compatibility
            String tenantId = (String) claims.get("tenant_id");  // snake_case (preferred)
            if (tenantId == null) {
                tenantId = (String) claims.get("tenantId");      // camelCase (legacy)
            }
            
            return tenantId;
            
        } catch (Exception e) {
            log.debug("Failed to extract tenant_id from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract client_id from JWT token without validation
     * This is used to determine which introspection endpoint to use
     */
    private String extractClientIdFromToken(String token) {
        try {
            // Decode JWT payload without verification (we'll validate via introspection)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String payload = parts[1];
            // Add padding if needed
            while (payload.length() % 4 != 0) {
                payload += "=";
            }
            
            byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(payload);
            String payloadJson = new String(decodedBytes);
            
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
            return (String) claims.get("client_id");
            
        } catch (Exception e) {
            log.debug("Failed to extract client_id from token: {}", e.getMessage());
            return null;
        }
    }
}
