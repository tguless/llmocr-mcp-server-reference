package com.llmocr.mcp.invoice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Client service to fetch S3 bucket configurations from LLMOCR main application.
 * 
 * This enables the "marrying" of tenants between LLMOCR and MCP Invoice Server,
 * allowing MCP Invoice Server to access the same S3 buckets as the main app.
 * 
 * Uses the /service/s3-config/tenant/{tenantId} endpoint on LLMOCR.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmOcrS3ConfigClient {

    private final RestTemplate restTemplate;
    private final com.llmocr.mcp.invoice.repository.TenantSettingsRepository tenantSettingsRepository;

    // Global defaults from config (tenant settings can override base URL)
    @Value("${llmocr.api.base-url:http://localhost:8080}")
    private String defaultLlmOcrBaseUrl;

    @Value("${llmocr.api.context-path:/llmocr/api}")
    private String llmOcrContextPath;

    // Cache S3 configs for 5 minutes to reduce API calls
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    /**
     * Get the LLMOCR API key for a tenant from their settings.
     */
    private String getTenantApiKey(String tenantId) {
        return tenantSettingsRepository.findByTenantId(tenantId)
                .map(com.llmocr.mcp.invoice.domain.TenantSettings::getLlmOcrApiKey)
                .orElse(null);
    }

    /**
     * Fetch S3 bucket configuration for a tenant from LLMOCR.
     * 
     * @param tenantId The tenant ID
     * @param bearerToken The user's Bearer token for authentication
     * @return Optional containing the bucket config, or empty if not found
     */
    public Optional<TenantBucketConfig> getTenantS3Config(String tenantId, String bearerToken) {
        // Check cache first
        CachedConfig cached = configCache.get(tenantId);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached S3 config for tenant: {}", tenantId);
            return Optional.of(cached.config);
        }

        for (String contextPathCandidate : contextPathCandidates()) {
            try {
                String url = defaultLlmOcrBaseUrl + contextPathCandidate + "/service/s3-config/tenant/" + tenantId;
                log.info("Fetching S3 config from LLMOCR for tenant: {} at URL: {}", tenantId, url);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                // Get tenant-specific API key from tenant settings
                String tenantApiKey = getTenantApiKey(tenantId);

                // Try tenant API key first (service-to-service), then fall back to bearer token
                if (tenantApiKey != null && !tenantApiKey.isEmpty()) {
                    headers.set("X-API-Key", tenantApiKey);
                    log.debug("Using tenant API key for LLMOCR authentication");
                } else if (bearerToken != null && !bearerToken.isEmpty()) {
                    // Fall back to bearer token (user's JWT)
                    String token = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
                    headers.set("Authorization", token);
                    log.debug("Using bearer token for LLMOCR authentication (no tenant API key configured)");
                } else {
                    log.warn("No authentication provided for LLMOCR API call - tenant {} has no API key configured", tenantId);
                }

                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    TenantBucketConfig config = parseBucketConfig(body);

                    // Cache the result
                    configCache.put(tenantId, new CachedConfig(config, Instant.now().toEpochMilli()));

                    log.info("Successfully fetched S3 config for tenant: {} - {} buckets",
                            tenantId, config.buckets.size());
                    return Optional.of(config);
                }
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                // Try next context path candidate.
            } catch (Exception e) {
                log.error("Failed to fetch S3 config from LLMOCR for tenant {}: {}", tenantId, e.getMessage());
                return Optional.empty();
            }
        }
        log.warn("No S3 config found for tenant: {}", tenantId);
        return Optional.empty();
    }

    /**
     * Get the primary bucket for a tenant (first active bucket).
     * 
     * @param tenantId The tenant ID
     * @param bearerToken The user's Bearer token for authentication
     * @return Optional containing the primary bucket info, or empty if not found
     */
    public Optional<BucketInfo> getPrimaryBucket(String tenantId, String bearerToken) {
        return getTenantS3Config(tenantId, bearerToken)
                .flatMap(config -> {
                    Optional<BucketInfo> invoiceSpecific = config.buckets.stream()
                            .filter(bucket -> bucket.prefix != null && bucket.prefix.toLowerCase().contains("/invoices/"))
                            .findFirst();
                    if (invoiceSpecific.isPresent()) {
                        return invoiceSpecific;
                    }
                    return config.buckets.stream()
                            .filter(bucket -> bucket.description != null
                                    && bucket.description.toLowerCase().contains("invoice"))
                            .findFirst()
                            .or(() -> config.buckets.stream().findFirst());
                });
    }

    /**
     * Clear cached config for a tenant (e.g., after bucket changes).
     */
    public void clearCache(String tenantId) {
        configCache.remove(tenantId);
        log.debug("Cleared S3 config cache for tenant: {}", tenantId);
    }

    /**
     * Clear all cached configs.
     */
    public void clearAllCache() {
        configCache.clear();
        log.info("Cleared all S3 config cache");
    }

    /**
     * Validate that a tenant has access to a specific bucket and prefix in LLMOCR.
     * 
     * SECURITY: This is used before allowing uploads to LLMOCR-exposed buckets.
     * 
     * @param tenantId The tenant ID
     * @param bucketName The bucket name to validate
     * @param requestedPrefix The prefix the tenant wants to access
     * @param bearerToken The user's Bearer token
     * @return ValidationResult with access status and allowed prefix
     */
    public BucketAccessValidation validateBucketAccess(String tenantId, String bucketName, 
                                                        String requestedPrefix, String bearerToken) {
        try {
            var config = getTenantS3Config(tenantId, bearerToken);
            
            if (config.isEmpty()) {
                return new BucketAccessValidation(false, null, 
                        "No bucket configuration found for tenant");
            }
            
            // Find matching bucket config
            for (BucketInfo bucket : config.get().buckets) {
                if (bucket.bucketName.equals(bucketName) && bucket.isActive) {
                    // SECURITY CHECK: Tenant's configured prefix must be a prefix of requested path
                    String tenantPrefix = bucket.prefix;
                    
                    if (tenantPrefix == null || tenantPrefix.isEmpty()) {
                        // No prefix restriction - this shouldn't happen with shared bucket
                        log.warn("SECURITY: Tenant {} has unrestricted access to bucket {} - this is unusual", 
                                tenantId, bucketName);
                        return new BucketAccessValidation(true, "", 
                                "Access granted (no prefix restriction)");
                    }
                    
                    // Normalize prefixes (ensure they end with /)
                    String normalizedTenantPrefix = tenantPrefix.endsWith("/") ? tenantPrefix : tenantPrefix + "/";
                    String normalizedRequestedPrefix = requestedPrefix == null || requestedPrefix.isEmpty() 
                            ? "" : (requestedPrefix.endsWith("/") ? requestedPrefix : requestedPrefix + "/");
                    
                    // For uploads, the requested prefix must START with the tenant's allowed prefix
                    // This prevents tenants from escaping their designated folder
                    if (normalizedRequestedPrefix.isEmpty()) {
                        // No prefix specified - force use of tenant prefix
                        return new BucketAccessValidation(true, normalizedTenantPrefix, 
                                "Access granted - using tenant prefix: " + normalizedTenantPrefix);
                    }
                    
                    if (normalizedRequestedPrefix.startsWith(normalizedTenantPrefix)) {
                        // Requested prefix is within tenant's allowed space
                        return new BucketAccessValidation(true, normalizedRequestedPrefix, 
                                "Access granted");
                    } else {
                        // Requested prefix is outside tenant's allowed space
                        log.warn("SECURITY: Tenant {} attempted to access prefix '{}' but is only allowed '{}'", 
                                tenantId, requestedPrefix, tenantPrefix);
                        return new BucketAccessValidation(false, normalizedTenantPrefix, 
                                "Access denied - you can only upload to prefix: " + tenantPrefix);
                    }
                }
            }
            
            return new BucketAccessValidation(false, null, 
                    "Bucket '" + bucketName + "' is not configured for your tenant");
            
        } catch (Exception e) {
            log.error("Failed to validate bucket access for tenant {}: {}", tenantId, e.getMessage());
            return new BucketAccessValidation(false, null, 
                    "Failed to validate bucket access: " + e.getMessage());
        }
    }

    /**
     * Result of bucket access validation
     */
    public static class BucketAccessValidation {
        public final boolean allowed;
        public final String effectivePrefix;  // The prefix that must be used
        public final String message;

        public BucketAccessValidation(boolean allowed, String effectivePrefix, String message) {
            this.allowed = allowed;
            this.effectivePrefix = effectivePrefix;
            this.message = message;
        }
    }

    /**
     * Upload file to LLMOCR bucket via the secure proxy endpoint.
     * 
     * SECURITY: This sends the file to LLMOCR backend which:
     * 1. Validates tenant access to bucket+prefix
     * 2. Performs the actual S3 write using LLMOCR's IAM credentials
     * 
     * MCP Invoice Server does NOT have direct S3 write access.
     * 
     * @param tenantId The tenant ID
     * @param bucketName Target bucket (optional if {@code promptTemplateName} / {@code promptTemplateId} set)
     * @param prefix Target prefix (ignored when uploading by template)
     * @param promptTemplateName PaperIQ prompt template name (e.g. {@code Invoice Processing})
     * @param promptTemplateId Stable template id from {@code /service/s3-config}
     * @return Upload result with S3 key and resolved bucket/prefix when available
     */
    public UploadResult uploadFile(String tenantId, String bucketName, String prefix,
                                   String filename, byte[] content, String contentType,
                                   String bearerToken, String sourceExternalId, String sourceHeadersJson,
                                   String promptTemplateName, Long promptTemplateId) {
        boolean byTemplate = promptTemplateId != null
                || (promptTemplateName != null && !promptTemplateName.isBlank());
        if (!byTemplate && (bucketName == null || bucketName.isBlank())) {
            return new UploadResult(false, null, null, null,
                    "bucketName is required unless promptTemplateName or promptTemplateId is set");
        }

        org.springframework.http.HttpHeaders headers = buildUploadHeaders(tenantId, bearerToken);

        List<String> pathErrors = new ArrayList<>();
        for (String contextPathCandidate : contextPathCandidates()) {
            org.springframework.util.MultiValueMap<String, Object> body =
                    buildUploadBody(filename, content, contentType, bucketName, prefix, sourceExternalId,
                            sourceHeadersJson, promptTemplateName, promptTemplateId);
            String url = defaultLlmOcrBaseUrl + contextPathCandidate + "/service/s3-upload/upload";
            log.info("Uploading file {} to LLMOCR proxy for tenant {} (template={}, bucket={} prefix={})",
                    filename, tenantId, byTemplate, bucketName, prefix);
            try {
                org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                        new org.springframework.http.HttpEntity<>(body, headers);

                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        org.springframework.http.HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();
                    boolean success = Boolean.TRUE.equals(responseBody.get("success"));

                    if (success) {
                        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                        String s3Key = data != null ? (String) data.get("s3Key") : null;
                        String resolvedBucket = bucketName;
                        String resolvedPrefix = null;
                        if (data != null) {
                            if (data.get("bucketName") instanceof String bn && !bn.isBlank()) {
                                resolvedBucket = bn;
                            }
                            if (data.get("prefix") instanceof String p) {
                                resolvedPrefix = p;
                            }
                        }
                        log.info("Successfully uploaded file {} to LLMOCR bucket {} key {}",
                                filename, resolvedBucket, s3Key);
                        return new UploadResult(true, s3Key, resolvedBucket, resolvedPrefix, null);
                    } else {
                        String error = (String) responseBody.get("error");
                        log.warn("LLMOCR upload failed: {}", error);
                        return new UploadResult(false, null, bucketName, null, error);
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                pathErrors.add("404 via " + contextPathCandidate);
            } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
                log.warn("SECURITY: Upload denied by LLMOCR for tenant {} bucket {}: {}",
                        tenantId, bucketName, e.getMessage());
                return new UploadResult(false, null, bucketName, null,
                        "Access denied by LLMOCR: " + e.getMessage());
            } catch (Exception e) {
                log.error("Failed to upload file to LLMOCR for tenant {}: {}", tenantId, e.getMessage());
                return new UploadResult(false, null, bucketName, null, "Upload failed: " + e.getMessage());
            }
        }
        return new UploadResult(false, null, bucketName, null,
                pathErrors.isEmpty() ? "Unexpected response from LLMOCR" : String.join("; ", pathErrors));
    }

    /**
     * @deprecated Prefer {@link #uploadFile(String, String, String, String, byte[], String, String, String, String, String, Long)} with template name.
     */
    @Deprecated
    public UploadResult uploadFile(String tenantId, String bucketName, String prefix,
                                   String filename, byte[] content, String contentType,
                                   String bearerToken, String sourceExternalId, String sourceHeadersJson) {
        return uploadFile(tenantId, bucketName, prefix, filename, content, contentType, bearerToken,
                sourceExternalId, sourceHeadersJson, null, null);
    }

    private org.springframework.http.HttpHeaders buildUploadHeaders(String tenantId, String bearerToken) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        String tenantApiKey = getTenantApiKey(tenantId);
        if (tenantApiKey != null && !tenantApiKey.isEmpty()) {
            headers.set("X-API-Key", tenantApiKey);
            log.debug("Using tenant API key for LLMOCR upload authentication");
        } else if (bearerToken != null && !bearerToken.isEmpty()) {
            String token = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
            headers.set("Authorization", token);
            log.debug("Using bearer token for LLMOCR upload authentication (no tenant API key configured)");
        } else {
            log.warn("No authentication provided for LLMOCR upload - tenant {} has no API key configured", tenantId);
        }
        return headers;
    }

    private org.springframework.util.MultiValueMap<String, Object> buildUploadBody(
            String filename, byte[] content, String contentType,
            String bucketName, String prefix,
            String sourceExternalId, String sourceHeadersJson,
            String promptTemplateName, Long promptTemplateId) {
        boolean byTemplate = promptTemplateId != null
                || (promptTemplateName != null && !promptTemplateName.isBlank());

        org.springframework.util.MultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        if (byTemplate) {
            if (promptTemplateId != null) {
                body.add("promptTemplateId", promptTemplateId.toString());
            }
            if (promptTemplateName != null && !promptTemplateName.isBlank()) {
                body.add("promptTemplateName", promptTemplateName.trim());
            }
        } else {
            body.add("bucketName", bucketName);
            if (prefix != null && !prefix.isEmpty()) {
                body.add("prefix", prefix);
            }
        }
        if (sourceExternalId != null && !sourceExternalId.isBlank()) {
            body.add("sourceExternalId", sourceExternalId.trim());
        }
        if (sourceHeadersJson != null && !sourceHeadersJson.isBlank()) {
            body.add("sourceHeadersJson", sourceHeadersJson.trim());
        }

        org.springframework.core.io.ByteArrayResource fileResource =
                new org.springframework.core.io.ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
        org.springframework.http.HttpHeaders fileHeaders = new org.springframework.http.HttpHeaders();
        fileHeaders.setContentType(org.springframework.http.MediaType.parseMediaType(
                contentType != null ? contentType : "application/octet-stream"));
        org.springframework.http.HttpEntity<org.springframework.core.io.ByteArrayResource> filePart =
                new org.springframework.http.HttpEntity<>(fileResource, fileHeaders);
        body.add("file", filePart);
        return body;
    }

    /**
     * Result of upload to LLMOCR proxy
     */
    public static class UploadResult {
        public final boolean success;
        public final String s3Key;
        public final String bucketName;
        /** Effective upload prefix when returned by PaperIQ (template-targeted uploads). */
        public final String prefix;
        public final String error;

        public UploadResult(boolean success, String s3Key, String bucketName, String prefix, String error) {
            this.success = success;
            this.s3Key = s3Key;
            this.bucketName = bucketName;
            this.prefix = prefix;
            this.error = error;
        }
    }

    private TenantBucketConfig parseBucketConfig(Map<String, Object> body) {
        TenantBucketConfig config = new TenantBucketConfig();
        config.tenantId = (String) body.get("tenant_id");
        config.buckets = new ArrayList<>();

        Object bucketsObj = body.get("buckets");
        if (bucketsObj instanceof List) {
            List<Map<String, Object>> bucketsList = (List<Map<String, Object>>) bucketsObj;
            for (Map<String, Object> b : bucketsList) {
                BucketInfo bucket = new BucketInfo();
                bucket.bucketName = (String) b.get("bucket_name");
                bucket.prefix = (String) b.get("prefix");
                bucket.description = (String) b.get("description");
                bucket.isActive = Boolean.TRUE.equals(b.get("is_active"));
                bucket.fileExtensions = (String) b.get("file_extensions");
                config.buckets.add(bucket);
            }
        }

        return config;
    }

    // DTOs for the response
    public static class TenantBucketConfig {
        public String tenantId;
        public List<BucketInfo> buckets = new ArrayList<>();
    }

    public static class BucketInfo {
        public String bucketName;
        public String prefix;
        public String description;
        public boolean isActive;
        public String fileExtensions;
    }

    private static class CachedConfig {
        final TenantBucketConfig config;
        final long timestamp;

        CachedConfig(TenantBucketConfig config, long timestamp) {
            this.config = config;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return Instant.now().toEpochMilli() - timestamp > CACHE_TTL_MS;
        }
    }

    private List<String> contextPathCandidates() {
        List<String> candidates = new ArrayList<>();
        String configured = normalizeContextPath(llmOcrContextPath);
        candidates.add(configured);
        if (!"/api".equals(configured)) {
            candidates.add("/api");
        }
        if (!"/llmocr/api".equals(configured)) {
            candidates.add("/llmocr/api");
        }
        return candidates;
    }

    private String normalizeContextPath(String value) {
        if (value == null || value.isBlank()) {
            return "/api";
        }
        String normalized = value.startsWith("/") ? value.trim() : "/" + value.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

