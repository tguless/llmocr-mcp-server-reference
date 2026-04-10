package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.config.S3ClientFactory;
import com.llmocr.mcp.invoice.config.S3Properties;
import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Service for retrieving PDFs from S3/MinIO buckets
 * 
 * Handles PDF retrieval and presigned URL generation for authorized access.
 * 
 * Supports two modes:
 * 1. Local configuration (S3BucketConfiguration) - uses stored credentials
 * 2. LLMOCR integration - queries LLMOCR API for bucket info, uses IAM role
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3PdfRetrievalService {

    private final S3BucketConfigurationRepository s3ConfigRepository;
    private final S3ClientFactory s3ClientFactory;
    private final S3Properties s3Properties;
    private final LlmOcrS3ConfigClient llmOcrS3ConfigClient;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Retrieve PDF content from S3 bucket
     * 
     * First tries local S3BucketConfiguration. If not found, falls back to 
     * LLMOCR API to get bucket info and uses IAM role for authentication.
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param bucketName S3 bucket name
     * @param objectKey S3 object key (path)
     * @return PDF file contents as byte array
     * @throws RuntimeException if retrieval fails or bucket not found
     */
    public byte[] retrievePdfFromS3(String tenantId, String bucketName, String objectKey) {
        return retrievePdfFromS3(tenantId, bucketName, objectKey, null);
    }

    /**
     * Retrieve PDF content from S3 bucket with optional bearer token for LLMOCR fallback
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param bucketName S3 bucket name
     * @param objectKey S3 object key (path)
     * @param bearerToken Bearer token for LLMOCR API authentication (for fallback)
     * @return PDF file contents as byte array
     * @throws RuntimeException if retrieval fails or bucket not found
     */
    public byte[] retrievePdfFromS3(String tenantId, String bucketName, String objectKey, String bearerToken) {
        enforceTenantPrefixConstraint(tenantId, bucketName, objectKey, bearerToken);

        String localError = null;
        String configuredEndpointError = null;
        String llmOcrError = null;

        // Try local S3 configuration first.
        try {
            Optional<S3BucketConfiguration> localConfig = s3ConfigRepository
                    .findByTenantIdAndBucketNameAndIsActiveTrue(tenantId, bucketName);
            if (localConfig.isPresent()) {
                try (S3Client localClient = createS3ClientFromLocalConfig(localConfig.get());
                     ResponseInputStream<GetObjectResponse> response = localClient.getObject(
                             GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())) {
                    byte[] pdfBytes = response.readAllBytes();
                    log.info("Successfully retrieved PDF from local-config bucket: {} key: {}", bucketName, objectKey);
                    return pdfBytes;
                } catch (Exception e) {
                    localError = e.getMessage();
                    log.warn("Local S3 config read failed for tenant {} bucket {} key {}: {}. Trying LLMOCR IAM fallback.",
                            tenantId, bucketName, objectKey, localError);
                }
            }
        } catch (Exception e) {
            // Keep local error info and continue to LLMOCR fallback.
            if (localError == null) {
                localError = e.getMessage();
            }
        }

        // Fall back to LLMOCR API configuration + IAM/default credentials.
        try {
            var llmOcrConfig = llmOcrS3ConfigClient.getTenantS3Config(tenantId, bearerToken);
            if (llmOcrConfig.isEmpty() || llmOcrConfig.get().buckets.isEmpty()) {
                throw new RuntimeException(
                        String.format("No bucket configuration found for tenant: %s, bucket: %s",
                                tenantId, bucketName));
            }

            boolean bucketFound = llmOcrConfig.get().buckets.stream()
                    .anyMatch(b -> b.bucketName.equals(bucketName) && b.isActive);
            if (!bucketFound) {
                throw new RuntimeException(
                        String.format("Bucket '%s' not configured for tenant: %s",
                                bucketName, tenantId));
            }

            // Localhost/dev fallback: try configured default endpoint (e.g., MinIO) first.
            try {
                try (S3Client configuredClient = createS3ClientFromDefaultEndpoint();
                     ResponseInputStream<GetObjectResponse> response = configuredClient.getObject(
                             GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())) {
                    byte[] pdfBytes = response.readAllBytes();
                    log.info("Successfully retrieved PDF via configured endpoint from bucket: {} key: {}", bucketName, objectKey);
                    return pdfBytes;
                }
            } catch (Exception e) {
                configuredEndpointError = e.getMessage();
                log.warn("Configured endpoint read failed for bucket {} key {}: {}. Trying IAM/default credentials.",
                        bucketName, objectKey, configuredEndpointError);
            }

            try (S3Client s3Client = S3Client.builder()
                    .credentialsProvider(DefaultCredentialsProvider.builder().build())
                    .region(Region.of(awsRegion))
                    .build();
                 ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                         GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())) {
                byte[] pdfBytes = response.readAllBytes();
                log.info("Successfully retrieved PDF from LLMOCR/IAM bucket: {} key: {}", bucketName, objectKey);
                return pdfBytes;
            }
        } catch (Exception e) {
            llmOcrError = e.getMessage();
            log.error("Failed to retrieve PDF from S3 bucket: {} key: {} - localError={}, configuredEndpointError={}, llmOcrError={}",
                    bucketName, objectKey, localError, configuredEndpointError, llmOcrError, e);
            throw new RuntimeException("Failed to retrieve PDF: " + summarizeS3Error(localError, configuredEndpointError, llmOcrError), e);
        }
    }

    private S3Client createS3ClientFromLocalConfig(S3BucketConfiguration config) {
        // Configure S3 for path-style access (required for MinIO)
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                config.getAccessKeyId(),
                                config.getSecretAccessKey()
                        )
                ))
                .region(Region.of(getRegionOrDefault(config.getRegion())))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    /**
     * Generate presigned URL for PDF access
     * 
     * Presigned URLs allow temporary access to private S3 objects without credentials.
     * URL is valid for 1 hour.
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param bucketName S3 bucket name
     * @param objectKey S3 object key (path)
     * @return Presigned URL for PDF access
     * @throws RuntimeException if generation fails or bucket not found
     */
    public String getPdfPresignedUrl(String tenantId, String bucketName, String objectKey) {
        return getPdfPresignedUrl(tenantId, bucketName, objectKey, null);
    }

    /**
     * Generate presigned URL for PDF access with optional bearer token for LLMOCR fallback
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param bucketName S3 bucket name
     * @param objectKey S3 object key (path)
     * @param bearerToken Bearer token for LLMOCR API authentication (for fallback)
     * @return Presigned URL for PDF access
     * @throws RuntimeException if generation fails or bucket not found
     */
    public String getPdfPresignedUrl(String tenantId, String bucketName, String objectKey, String bearerToken) {
        enforceTenantPrefixConstraint(tenantId, bucketName, objectKey, bearerToken);

        String localError = null;
        String configuredEndpointError = null;
        String llmOcrError = null;

        // Try local S3 configuration first.
        try {
            Optional<S3BucketConfiguration> localConfig = s3ConfigRepository
                    .findByTenantIdAndBucketNameAndIsActiveTrue(tenantId, bucketName);
            if (localConfig.isPresent()) {
                try (S3Presigner localPresigner = createPresignerFromLocalConfig(localConfig.get())) {
                    GetObjectRequest request = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build();
                    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofHours(1))
                            .getObjectRequest(request)
                            .build();
                    PresignedGetObjectRequest presignedRequest = localPresigner.presignGetObject(presignRequest);
                    String presignedUrl = presignedRequest.url().toString();
                    log.info("Successfully generated local-config presigned URL for bucket: {} key: {}", bucketName, objectKey);
                    return presignedUrl;
                } catch (Exception e) {
                    localError = e.getMessage();
                    log.warn("Local presign failed for tenant {} bucket {} key {}: {}. Trying LLMOCR IAM fallback.",
                            tenantId, bucketName, objectKey, localError);
                }
            }
        } catch (Exception e) {
            if (localError == null) {
                localError = e.getMessage();
            }
        }

        // Fall back to LLMOCR API configuration + IAM/default credentials.
        try {
            var llmOcrConfig = llmOcrS3ConfigClient.getTenantS3Config(tenantId, bearerToken);
            if (llmOcrConfig.isEmpty() || llmOcrConfig.get().buckets.isEmpty()) {
                throw new RuntimeException(
                        String.format("No bucket configuration found for tenant: %s, bucket: %s",
                                tenantId, bucketName));
            }
            boolean bucketFound = llmOcrConfig.get().buckets.stream()
                    .anyMatch(b -> b.bucketName.equals(bucketName) && b.isActive);
            if (!bucketFound) {
                throw new RuntimeException(
                        String.format("Bucket '%s' not configured for tenant: %s",
                                bucketName, tenantId));
            }

            // Localhost/dev fallback: try configured default endpoint (e.g., MinIO) first.
            try {
                try (S3Presigner configuredPresigner = createPresignerFromDefaultEndpoint()) {
                    GetObjectRequest request = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build();
                    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofHours(1))
                            .getObjectRequest(request)
                            .build();
                    PresignedGetObjectRequest presignedRequest = configuredPresigner.presignGetObject(presignRequest);
                    String presignedUrl = presignedRequest.url().toString();
                    log.info("Successfully generated configured-endpoint presigned URL for bucket: {} key: {}", bucketName, objectKey);
                    return presignedUrl;
                }
            } catch (Exception e) {
                configuredEndpointError = e.getMessage();
                log.warn("Configured endpoint presign failed for bucket {} key {}: {}. Trying IAM/default credentials.",
                        bucketName, objectKey, configuredEndpointError);
            }

            try (S3Presigner presigner = S3Presigner.builder()
                    .credentialsProvider(DefaultCredentialsProvider.builder().build())
                    .region(Region.of(awsRegion))
                    .build()) {
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(1))
                        .getObjectRequest(request)
                        .build();
                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
                String presignedUrl = presignedRequest.url().toString();
                log.info("Successfully generated LLMOCR/IAM presigned URL for bucket: {} key: {}", bucketName, objectKey);
                return presignedUrl;
            }
        } catch (Exception e) {
            llmOcrError = e.getMessage();
            log.error("Failed to generate presigned URL for bucket: {} key: {} - localError={}, configuredEndpointError={}, llmOcrError={}",
                    bucketName, objectKey, localError, configuredEndpointError, llmOcrError, e);
            throw new RuntimeException("Failed to generate presigned URL: " + summarizeS3Error(localError, configuredEndpointError, llmOcrError), e);
        }
    }

    private S3Presigner createPresignerFromLocalConfig(S3BucketConfiguration config) {
        // Configure S3 for path-style access (required for MinIO)
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                config.getAccessKeyId(),
                                config.getSecretAccessKey()
                        )
                ))
                .region(Region.of(getRegionOrDefault(config.getRegion())))
                .endpointOverride(URI.create(config.getEndpoint()))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    /**
     * Get all available buckets for a tenant from BOTH local config AND LLMOCR.
     * 
     * This enables tenants to access:
     * 1. Their own custom S3/MinIO buckets (local config with stored credentials)
     * 2. LLMOCR's shared bucket (accessed via IAM role)
     * 
     * @param tenantId The tenant ID
     * @param bearerToken Bearer token for LLMOCR API (optional)
     * @return List of all available bucket infos from both sources
     */
    public java.util.List<AvailableBucket> getAllAvailableBuckets(String tenantId, String bearerToken) {
        java.util.List<AvailableBucket> allBuckets = new java.util.ArrayList<>();
        java.util.Set<String> seenBucketKeys = new java.util.HashSet<>();

        // 1. Add buckets from local configuration
        var localConfigs = s3ConfigRepository.findByTenantIdAndIsActiveTrue(tenantId);
        for (S3BucketConfiguration config : localConfigs) {
            String key = config.getBucketName() + "|" + (config.getDescription() != null ? config.getDescription() : "");
            if (!seenBucketKeys.contains(key)) {
                allBuckets.add(new AvailableBucket(
                        config.getBucketName(),
                        null,  // No prefix in local config
                        config.getDescription(),
                        "local",
                        true
                ));
                seenBucketKeys.add(key);
            }
        }

        // 2. Add buckets from LLMOCR (if not already present)
        try {
            var llmOcrConfig = llmOcrS3ConfigClient.getTenantS3Config(tenantId, bearerToken);
            if (llmOcrConfig.isPresent()) {
                for (var bucket : llmOcrConfig.get().buckets) {
                    String key = bucket.bucketName + "|" + (bucket.prefix != null ? bucket.prefix : "");
                    if (!seenBucketKeys.contains(key)) {
                        allBuckets.add(new AvailableBucket(
                                bucket.bucketName,
                                bucket.prefix,
                                bucket.description,
                                "llmocr",
                                bucket.isActive
                        ));
                        seenBucketKeys.add(key);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch LLMOCR bucket config for tenant {}: {}", tenantId, e.getMessage());
            // Continue with just local buckets
        }

        log.info("Found {} available buckets for tenant {} (local + LLMOCR)", allBuckets.size(), tenantId);
        return allBuckets;
    }

    /**
     * Check if a bucket is accessible by the tenant from either source.
     * 
     * @param tenantId The tenant ID
     * @param bucketName The bucket name to check
     * @param bearerToken Bearer token for LLMOCR API (optional)
     * @return true if bucket is accessible from either local config or LLMOCR
     */
    public boolean isBucketAccessible(String tenantId, String bucketName, String bearerToken) {
        // Check local config first
        Optional<S3BucketConfiguration> localConfig = s3ConfigRepository
                .findByTenantIdAndBucketNameAndIsActiveTrue(tenantId, bucketName);
        
        if (localConfig.isPresent()) {
            return true;
        }

        // Check LLMOCR config
        try {
            var llmOcrConfig = llmOcrS3ConfigClient.getTenantS3Config(tenantId, bearerToken);
            if (llmOcrConfig.isPresent()) {
                return llmOcrConfig.get().buckets.stream()
                        .anyMatch(b -> b.bucketName.equals(bucketName) && b.isActive);
            }
        } catch (Exception e) {
            log.warn("Failed to check LLMOCR bucket access for tenant {}: {}", tenantId, e.getMessage());
        }

        return false;
    }

    /**
     * DTO for available bucket information
     */
    public static class AvailableBucket {
        public final String bucketName;
        public final String prefix;
        public final String description;
        public final String source;  // "local" or "llmocr"
        public final boolean isActive;

        public AvailableBucket(String bucketName, String prefix, String description, String source, boolean isActive) {
            this.bucketName = bucketName;
            this.prefix = prefix;
            this.description = description;
            this.source = source;
            this.isActive = isActive;
        }
    }

    /**
     * Get region or use default (us-east-1) if null or empty
     */
    private String getRegionOrDefault(String region) {
        if (region == null || region.trim().isEmpty()) {
            return "us-east-1";
        }
        return region.trim();
    }

    private String summarizeS3Error(String localError, String configuredEndpointError, String llmOcrError) {
        if (isBucketMissing(localError) && isBucketMissing(configuredEndpointError) && isBucketMissing(llmOcrError)) {
            return "The specified bucket does not exist";
        }
        if (configuredEndpointError != null && !configuredEndpointError.isBlank()) {
            return configuredEndpointError;
        }
        if (llmOcrError != null && !llmOcrError.isBlank()) {
            return llmOcrError;
        }
        if (localError != null && !localError.isBlank()) {
            return localError;
        }
        return "unknown S3 retrieval error";
    }

    private S3Client createS3ClientFromDefaultEndpoint() {
        String endpoint = s3Properties.getDefaultEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new RuntimeException("No s3.default-endpoint configured");
        }
        String accessKey = s3Properties.getDefaultAccessKey();
        String secretKey = s3Properties.getDefaultSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new RuntimeException("No s3.default-access-key/s3.default-secret-key configured");
        }
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .region(Region.of(awsRegion))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    private S3Presigner createPresignerFromDefaultEndpoint() {
        String endpoint = s3Properties.getDefaultEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new RuntimeException("No s3.default-endpoint configured");
        }
        String accessKey = s3Properties.getDefaultAccessKey();
        String secretKey = s3Properties.getDefaultSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new RuntimeException("No s3.default-access-key/s3.default-secret-key configured");
        }
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .region(Region.of(awsRegion))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    private boolean isBucketMissing(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("specified bucket does not exist")
                || normalized.contains("nosuchbucket")
                || normalized.contains("bucket does not exist");
    }

    /**
     * Defense-in-depth read guard:
     * If LLMOCR reports a tenant prefix for this bucket, enforce that the object key
     * stays inside that prefix before any S3 read/presign is attempted.
     */
    private void enforceTenantPrefixConstraint(String tenantId, String bucketName, String objectKey, String bearerToken) {
        try {
            Optional<LlmOcrS3ConfigClient.TenantBucketConfig> configOpt = llmOcrS3ConfigClient.getTenantS3Config(tenantId, bearerToken);
            if (configOpt.isEmpty()) {
                return;
            }

            java.util.List<LlmOcrS3ConfigClient.BucketInfo> bucketMappings = configOpt.get().buckets.stream()
                    .filter(bucket -> bucket != null && bucket.isActive)
                    .filter(bucket -> bucketName.equals(bucket.bucketName))
                    .toList();
            if (bucketMappings.isEmpty()) {
                return;
            }

            String normalizedObjectKey = normalizeObjectKey(objectKey);
            java.util.List<String> constrainedPrefixes = bucketMappings.stream()
                    .map(mapping -> normalizeS3Prefix(mapping.prefix))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (constrainedPrefixes.isEmpty()) {
                // No prefix constraints defined for this tenant+bucket.
                return;
            }

            boolean matchesAnyPrefix = constrainedPrefixes.stream()
                    .anyMatch(normalizedObjectKey::startsWith);
            if (!matchesAnyPrefix) {
                String message = String.format(
                        "Access denied - object key '%s' is outside tenant prefixes %s for bucket '%s'",
                        objectKey, constrainedPrefixes, bucketName);
                log.warn("SECURITY: {}", message);
                throw new RuntimeException(message);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // If config lookup fails, keep existing behavior (bucket-level checks still apply).
            log.warn("Could not enforce prefix guard for tenant {} bucket {} key {}: {}",
                    tenantId, bucketName, objectKey, e.getMessage());
        }
    }

    private String normalizeS3Prefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String normalized = prefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeObjectKey(String objectKey) {
        if (objectKey == null) {
            return "";
        }
        String normalized = objectKey.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}

