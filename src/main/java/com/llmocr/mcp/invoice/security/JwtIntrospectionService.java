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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final com.llmocr.mcp.invoice.repository.TenantRepository tenantRepository;

    // All configuration now database-driven via MCP server frontend
    @Value("${server.servlet.context-path:/mcp-invoice}")
    private String contextPath;
    
    @Value("${server.port:8081}")
    private int serverPort;
    
    @Value("${security.jwt.introspection-endpoint:http://localhost:8080/api/jwt/validate}")
    private String defaultIntrospectionEndpoint;

    /**
     * Optional comma-separated list of additional accepted audiences.
     * Example: https://paperiq.ai/mcp-invoice,https://eyesense.ai/mcp-invoice
     */
    @Value("${security.jwt.allowed-audiences:}")
    private String allowedAudiences;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("🔧 JwtIntrospectionService initialized with introspection endpoint: {}", defaultIntrospectionEndpoint);
    }

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
                    // Fallback to default endpoint (configured per environment via application.yml)
                    introspectionEndpoint = defaultIntrospectionEndpoint;
                    log.debug("Client '{}' for tenant '{}' not found or no endpoint configured, using default: {}", 
                             clientId, tenantId, introspectionEndpoint);
                }
            } else {
                // Legacy flow: Use default introspection endpoint (configured per environment)
                introspectionEndpoint = defaultIntrospectionEndpoint;
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
                    if (tenantId == null || tenantId.isBlank()) {
                        Object tenantClaim = body.get("tenant_id");
                        if (tenantClaim == null) {
                            tenantClaim = body.get("tenantId");
                        }
                        if (tenantClaim != null) {
                            tenantId = String.valueOf(tenantClaim);
                        }
                    }
                    if (clientId == null || clientId.isBlank()) {
                        Object clientClaim = body.get("client_id");
                        if (clientClaim == null) {
                            clientClaim = body.get("clientId");
                        }
                        if (clientClaim != null) {
                            clientId = String.valueOf(clientClaim);
                        }
                    }
                    String redirectUri = body.get("redirect_uri") == null ? null : String.valueOf(body.get("redirect_uri"));
                    List<String> authorizedRedirectUrisFromToken = parseStringListClaim(body.get("authorized_redirect_uris"));
                    String ssoProviderIssuer = body.get("sso_provider_issuer") == null ? null : String.valueOf(body.get("sso_provider_issuer"));
                    
                    log.debug("Extracted from token - userId: {}, email: {}, tenantId: {}, issuer: {}", 
                             userId, email, tenantId, issuer);
                    
                    // Validate audience and client access
                    String audience = (String) body.get("aud");
                    
                    // Validate issuer against client-specific trusted issuer (if we have client config)
                    if (authorizedClient != null) {
                        String trustedIssuer = authorizedClient.getTrustedIssuer();
                        // Skip issuer validation if trusted_issuer is a default/placeholder value (localhost or docker-internal)
                        // This allows the env var-based configuration to work without requiring database changes
                        boolean isDefaultPlaceholder = trustedIssuer == null || 
                                trustedIssuer.trim().isEmpty() ||
                                trustedIssuer.contains("localhost") || 
                                trustedIssuer.contains("llm-ocr-backend");
                        
                        if (isDefaultPlaceholder) {
                            log.debug("Skipping issuer validation - trusted_issuer is a default placeholder: {}", trustedIssuer);
                        } else if (!issuer.equals(trustedIssuer)) {
                            log.warn("Token from untrusted issuer for client '{}'. Expected: {}, got: {}", 
                                    clientId, trustedIssuer, issuer);
                            return TokenValidationResult.invalid("Token from untrusted issuer for this client");
                        }
                    } else {
                        log.debug("No client configuration found, skipping issuer validation for backward compatibility");
                    }
                    
                    // AUTO-CREATE TENANT & CLIENT: If tenantId is present, ensure tenant and client authorization exist
                    // This must happen BEFORE validateClientAccess() check
                    if (tenantId != null && !tenantId.isEmpty() && clientId != null) {
                        ensureTenantAndClientExist(tenantId, clientId, email, issuer, audience, authorizedRedirectUrisFromToken);
                    }
                    
                    // Validate audience and final client access (only if we have client info)
                    if (tenantId != null && clientId != null) {
                        if (!validateClientAccess(
                                audience,
                                issuer,
                                clientId,
                                tenantId,
                                redirectUri,
                                authorizedRedirectUrisFromToken,
                                ssoProviderIssuer
                        )) {
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
    private boolean validateClientAccess(
            String audience,
            String issuer,
            String clientId,
            String tenantId,
            String redirectUri,
            List<String> authorizedRedirectUrisFromToken,
            String ssoProviderIssuer
    ) {
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

        // Prefer tenant/client DB trust configuration first.
        // Token hint claim is fallback only when DB config is not set.
        String trustedIssuer = authorizedClient.getTrustedIssuer();
        String expectedIssuer = (trustedIssuer != null && !trustedIssuer.isBlank())
                ? trustedIssuer
                : ssoProviderIssuer;
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !expectedIssuer.equals(issuer)) {
            log.warn("SECURITY: SSO issuer mismatch. Expected {}, got {}", expectedIssuer, issuer);
            return false;
        }
        
        // Check audience claim (OAuth 2.1 compliance)
        // Audience must match the expected audience URL from the database
        String expectedAudience = authorizedClient.getAudienceUrl();
        Set<String> configuredAllowedAudiences = parseAllowedAudiences();
        String localhostAudience = "http://localhost:" + serverPort + contextPath;
        String dockerAudience = "http://mcp-invoice-server:" + serverPort + contextPath;
        String issuerDerivedAudience = deriveAudienceFromIssuer(issuer);
        
        // If no audience URL is configured in database, fall back to server URL
        if (expectedAudience == null || expectedAudience.trim().isEmpty()) {
            // Support both localhost and Docker service name for backward compatibility
            boolean audienceValid = audience != null && 
                    (audience.equals(localhostAudience)
                            || audience.equals(dockerAudience)
                            || (issuerDerivedAudience != null && audience.equals(issuerDerivedAudience))
                            || configuredAllowedAudiences.contains(audience));
            
            if (!audienceValid) {
                log.warn("SECURITY: Token audience mismatch (fallback validation). Expected: {} or {}{}{} , got: {}",
                        localhostAudience,
                        dockerAudience,
                        issuerDerivedAudience != null ? " or " + issuerDerivedAudience : "",
                        configuredAllowedAudiences.isEmpty() ? "" : " or one of " + configuredAllowedAudiences,
                        audience);
                return false;
            }
            
            log.debug("SECURITY: Token audience validated using fallback: {}", audience);
            // Self-heal legacy authorized clients by persisting audience once validated.
            authorizedClient.setAudienceUrl(audience);
            authorizedClientRepository.save(authorizedClient);
        } else {
            // Use configured audience URL from database
            boolean expectedMatches = audience != null && audience.equals(expectedAudience);
            boolean allowedFallbackMatches = audience != null &&
                    (configuredAllowedAudiences.contains(audience)
                            || audience.equals(localhostAudience)
                            || audience.equals(dockerAudience)
                            || (issuerDerivedAudience != null && audience.equals(issuerDerivedAudience)));

            if (!expectedMatches && !allowedFallbackMatches) {
                log.warn("SECURITY: Token audience mismatch. Expected: {}, got: {}", expectedAudience, audience);
                return false;
            }

            // Self-heal stale tenant/client audience_url if token audience is otherwise trusted.
            if (!expectedMatches && allowedFallbackMatches) {
                log.info("SECURITY: Updating stale audience_url for tenant '{}' client '{}' from '{}' to '{}'",
                        tenantId, clientId, expectedAudience, audience);
                authorizedClient.setAudienceUrl(audience);
                authorizedClientRepository.save(authorizedClient);
            }
            
            log.debug("SECURITY: Token audience validated against database configuration: {}", audience);
        }
        
        if (redirectUri != null && !redirectUri.isBlank()) {
            List<String> allowedRedirects = parseStringList(authorizedClient.getAuthorizedRedirectUris());
            if (allowedRedirects.isEmpty() && !authorizedRedirectUrisFromToken.isEmpty()) {
                authorizedClient.setAuthorizedRedirectUris(serializeStringList(authorizedRedirectUrisFromToken));
                authorizedClientRepository.save(authorizedClient);
                allowedRedirects = authorizedRedirectUrisFromToken;
            }

            if (!allowedRedirects.isEmpty() && !allowedRedirects.contains(redirectUri)) {
                // Self-heal stale redirect allowlists when token carries a trusted list that includes this redirect URI.
                if (!authorizedRedirectUrisFromToken.isEmpty() && authorizedRedirectUrisFromToken.contains(redirectUri)) {
                    java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(allowedRedirects);
                    merged.addAll(authorizedRedirectUrisFromToken);
                    merged.add(redirectUri);
                    authorizedClient.setAuthorizedRedirectUris(serializeStringList(merged.stream().toList()));
                    authorizedClientRepository.save(authorizedClient);
                    log.info("SECURITY: Updated stale redirect allowlist for tenant '{}' client '{}' with URI '{}'",
                            tenantId, clientId, redirectUri);
                    return true;
                }
                log.warn("SECURITY: Redirect URI '{}' is not in authorized allowlist {}", redirectUri, allowedRedirects);
                return false;
            }
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
    
    /**
     * Ensure tenant and authorized client exist in the database. If not, create them automatically.
     * This enables automatic tenant and client provisioning when a new user's JWT token is validated.
     * 
     * @param tenantId The tenant ID from the JWT token
     * @param clientId The client ID from the JWT token
     * @param email The user's email for auditing purposes
     * @param issuer The JWT issuer for client authorization
     */
    @org.springframework.transaction.annotation.Transactional
    private void ensureTenantAndClientExist(
            String tenantId,
            String clientId,
            String email,
            String issuer,
            String audience,
            List<String> authorizedRedirectUris
    ) {
        try {
            // Step 1: Ensure tenant exists
            if (!tenantRepository.existsByTenantId(tenantId)) {
                com.llmocr.mcp.invoice.domain.Tenant newTenant = com.llmocr.mcp.invoice.domain.Tenant.builder()
                        .tenantId(tenantId)
                        .tenantName(tenantId) // Use tenantId as default name
                        .description("Auto-created tenant for user: " + (email != null ? email : "unknown"))
                        .active(true)
                        .createdBy("auto-provision")
                        .updatedBy("auto-provision")
                        .build();
                
                tenantRepository.save(newTenant);
                log.info("✅ AUTO-PROVISIONED TENANT: Created new tenant '{}' for user '{}'", tenantId, email);
            } else {
                log.debug("Tenant '{}' already exists", tenantId);
            }
            
            // Step 2: Ensure authorized client exists for this tenant
            var existingClient = authorizedClientRepository
                    .findByTenantIdAndClientIdAndIsActiveTrue(tenantId, clientId);
            
            if (existingClient.isEmpty()) {
                // Create authorized client with the issuer from the validated token
                com.llmocr.mcp.invoice.domain.AuthorizedClient newClient = 
                        com.llmocr.mcp.invoice.domain.AuthorizedClient.builder()
                        .tenantId(tenantId)
                        .clientId(clientId)
                        .clientName(clientId) // Default to using clientId as name
                        .description("Auto-created client authorization for tenant: " + tenantId)
                        .trustedIssuer(issuer != null ? issuer : "http://localhost:8080/api")
                        .introspectionEndpoint(issuer != null ? issuer + "/jwt/validate" : defaultIntrospectionEndpoint)
                        .audienceUrl(audience)
                        .authorizedRedirectUris(serializeStringList(authorizedRedirectUris))
                        .isActive(true)
                        .createdBy("auto-provision")
                        .build();
                
                authorizedClientRepository.save(newClient);
                log.info("✅ AUTO-PROVISIONED CLIENT: Authorized client '{}' for tenant '{}'", clientId, tenantId);
            } else {
                log.debug("Client '{}' already authorized for tenant '{}'", clientId, tenantId);
                if (!authorizedRedirectUris.isEmpty()) {
                    var existing = existingClient.get();
                    List<String> currentUris = parseStringList(existing.getAuthorizedRedirectUris());
                    if (currentUris.isEmpty()) {
                        existing.setAuthorizedRedirectUris(serializeStringList(authorizedRedirectUris));
                        authorizedClientRepository.save(existing);
                    }
                }
            }
            
        } catch (Exception e) {
            // Log error but don't fail the authentication
            // Resources might have been created by another concurrent request
            log.warn("Failed to auto-provision tenant/client for '{}'/'{}'': {} (this may be expected if just created)", 
                    tenantId, clientId, e.getMessage());
        }
    }

    private String deriveAudienceFromIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        try {
            java.net.URI issuerUri = java.net.URI.create(issuer);
            String host = issuerUri.getHost();
            String scheme = issuerUri.getScheme();
            int port = issuerUri.getPort();
            if (host == null || scheme == null) {
                return null;
            }
            String authority = port > 0 ? host + ":" + port : host;
            return scheme + "://" + authority + contextPath;
        } catch (Exception e) {
            log.debug("Failed to derive audience from issuer '{}': {}", issuer, e.getMessage());
            return null;
        }
    }

    private Set<String> parseAllowedAudiences() {
        if (allowedAudiences == null || allowedAudiences.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedAudiences.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<String> parseStringListClaim(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of();
    }

    private List<String> parseStringList(String rawJsonArray) {
        if (rawJsonArray == null || rawJsonArray.isBlank()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> parsed = objectMapper.readValue(rawJsonArray, List.class);
            return parsed.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.debug("Failed to parse redirect URI list: {}", e.getMessage());
            return List.of();
        }
    }

    private String serializeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return null;
        }
    }
}
