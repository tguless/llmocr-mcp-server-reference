package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.UUID;

/**
 * Service for managing tenant logos in S3/MinIO
 * Handles upload, retrieval, and deletion of logo files
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3LogoService {

    private final S3BucketConfigurationRepository s3ConfigRepository;
    
    private static final String LOGOS_BUCKET = "tenant-logos";
    private static final String LOGO_PREFIX = "logos/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "image/png", "image/jpeg", "image/jpg", "image/svg+xml", "image/webp"
    };

    /**
     * Upload a logo file to S3
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param file The logo file to upload
     * @param logoType Type of logo ("main" or "login")
     * @return S3 object key for the uploaded logo
     */
    public String uploadLogo(String tenantId, MultipartFile file, String logoType) {
        S3Client s3Client = null;
        try {
            // Validate file
            validateLogoFile(file);

            // Generate unique filename
            String fileExtension = getFileExtension(file.getOriginalFilename());
            String fileName = String.format("%s%s/%s-%s.%s", 
                LOGO_PREFIX, 
                tenantId, 
                logoType,
                UUID.randomUUID().toString(),
                fileExtension
            );

            log.info("Uploading logo for tenant {} to S3: {}", tenantId, fileName);

            // Get S3 configuration
            S3BucketConfiguration config = getS3Config(tenantId);

            // Create S3 client
            s3Client = createS3Client(config);

            // Ensure bucket exists
            ensureBucketExists(s3Client, config);

            // Upload file
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(LOGOS_BUCKET)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

            log.info("Successfully uploaded logo to S3: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to upload logo for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload logo: " + e.getMessage(), e);

        } finally {
            if (s3Client != null) {
                try {
                    s3Client.close();
                } catch (Exception e) {
                    log.warn("Error closing S3 client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Retrieve a logo from S3
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param objectKey S3 object key
     * @return Logo file contents as byte array
     */
    public byte[] retrieveLogo(String tenantId, String objectKey) {
        S3Client s3Client = null;
        try {
            log.debug("Retrieving logo from S3: {}", objectKey);

            // Get S3 configuration
            S3BucketConfiguration config = getS3Config(tenantId);

            // Create S3 client
            s3Client = createS3Client(config);

            // Get object from hardcoded tenant-logos bucket
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(LOGOS_BUCKET)
                    .key(objectKey)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
            byte[] logoBytes = response.readAllBytes();

            log.info("Successfully retrieved logo from S3 bucket {} key {}", LOGOS_BUCKET, objectKey);
            return logoBytes;

        } catch (Exception e) {
            log.error("Failed to retrieve logo from S3 bucket {} key {}: {}", LOGOS_BUCKET, objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve logo: " + e.getMessage(), e);

        } finally {
            if (s3Client != null) {
                try {
                    s3Client.close();
                } catch (Exception e) {
                    log.warn("Error closing S3 client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Delete a logo from S3
     * 
     * @param tenantId Tenant ID for multi-tenant isolation
     * @param objectKey S3 object key
     */
    public void deleteLogo(String tenantId, String objectKey) {
        S3Client s3Client = null;
        try {
            log.info("Deleting logo from S3: {}", objectKey);

            // Get S3 configuration
            S3BucketConfiguration config = getS3Config(tenantId);

            // Create S3 client
            s3Client = createS3Client(config);

            // Delete object
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(LOGOS_BUCKET)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);

            log.info("Successfully deleted logo from S3: {}", objectKey);

        } catch (Exception e) {
            log.error("Failed to delete logo from S3: {} - {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to delete logo: " + e.getMessage(), e);

        } finally {
            if (s3Client != null) {
                try {
                    s3Client.close();
                } catch (Exception e) {
                    log.warn("Error closing S3 client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Get content type for a logo file
     */
    public String getLogoContentType(String tenantId, String objectKey) {
        String extension = getFileExtension(objectKey);
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    // Private helper methods

    private void validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("Logo file size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        String contentType = file.getContentType();
        boolean isAllowedType = false;
        for (String allowedType : ALLOWED_CONTENT_TYPES) {
            if (allowedType.equals(contentType)) {
                isAllowedType = true;
                break;
            }
        }

        if (!isAllowedType) {
            throw new IllegalArgumentException(
                "Invalid file type. Allowed types: PNG, JPEG, SVG, WebP"
            );
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "png";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private S3BucketConfiguration getS3Config(String tenantId) {
        // For logos, we'll use the first available S3 configuration for the tenant
        // or create a default one if none exists
        return s3ConfigRepository
                .findByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "No S3 configuration found for tenant: " + tenantId + 
                    ". Please configure S3 bucket settings first."
                ));
    }

    private S3Client createS3Client(S3BucketConfiguration config) {
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

    private void ensureBucketExists(S3Client s3Client, S3BucketConfiguration config) {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(LOGOS_BUCKET)
                    .build();
            s3Client.headBucket(headRequest);
            log.debug("Bucket {} already exists", LOGOS_BUCKET);

        } catch (NoSuchBucketException e) {
            log.info("Creating bucket: {}", LOGOS_BUCKET);
            CreateBucketRequest createRequest = CreateBucketRequest.builder()
                    .bucket(LOGOS_BUCKET)
                    .build();
            s3Client.createBucket(createRequest);
            log.info("Successfully created bucket: {}", LOGOS_BUCKET);
        }
    }

    private String getRegionOrDefault(String region) {
        if (region == null || region.trim().isEmpty()) {
            return "us-east-1";
        }
        return region.trim();
    }
}

