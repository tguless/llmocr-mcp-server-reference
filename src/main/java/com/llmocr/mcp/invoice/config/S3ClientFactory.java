package com.llmocr.mcp.invoice.config;

import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

/**
 * Factory for creating S3 clients from bucket configurations
 * 
 * Dynamically creates S3Client instances based on bucket configuration details.
 * Supports both AWS S3 and MinIO endpoints.
 */
@Service
@Slf4j
public class S3ClientFactory {

    /**
     * Create an S3Client from a bucket configuration
     * 
     * @param config The S3 bucket configuration containing endpoint and credentials
     * @return A configured S3Client instance
     * @throws RuntimeException if client creation fails
     */
    public S3Client getS3Client(S3BucketConfiguration config) {
        try {
            log.debug("Creating S3 client for bucket: {} at endpoint: {}", 
                    config.getBucketName(), config.getEndpoint());

            // Create credentials from configuration
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                    config.getAccessKeyId(),
                    config.getSecretAccessKey()
            );

            // Configure S3 for path-style access (required for MinIO)
            S3Configuration s3Configuration = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build();

            // Build and return S3Client
            return S3Client.builder()
                    .endpointOverride(URI.create(config.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .region(Region.of(getRegionOrDefault(config.getRegion())))
                    .serviceConfiguration(s3Configuration)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create S3 client for bucket {}: {}", 
                    config.getBucketName(), e.getMessage(), e);
            throw new RuntimeException("Failed to create S3 client: " + e.getMessage(), e);
        }
    }

    /**
     * Test connection to S3 bucket
     * 
     * @param config The S3 bucket configuration to test
     * @return true if connection is successful, false otherwise
     */
    public boolean testConnection(S3BucketConfiguration config) {
        S3Client s3Client = null;
        try {
            log.info("Testing connection to bucket: {} at {}", config.getBucketName(), config.getEndpoint());
            
            s3Client = getS3Client(config);
            
            // Test with HeadBucket call
            s3Client.headBucket(req -> req.bucket(config.getBucketName()));
            
            log.info("Successfully tested connection to bucket: {}", config.getBucketName());
            return true;

        } catch (Exception e) {
            log.warn("Failed to test connection to bucket {}: {}", 
                    config.getBucketName(), e.getMessage());
            return false;

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
     * Verify bucket exists (do NOT create - buckets should be managed by infrastructure)
     * 
     * SECURITY: We no longer auto-create buckets because:
     * 1. ECS/application should not have s3:CreateBucket permission
     * 2. Bucket creation should be done via Terraform/CloudFormation or admin
     * 3. Prevents tenants from creating arbitrary buckets
     * 
     * @param config The S3 bucket configuration
     * @param s3Client The S3Client instance to use
     * @return true if bucket exists, false otherwise
     */
    public boolean ensureBucketExists(S3BucketConfiguration config, S3Client s3Client) {
        try {
            String bucketName = config.getBucketName();
            log.debug("Verifying bucket {} exists", bucketName);

            // Check if bucket exists
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(headBucketRequest);
            log.debug("Bucket {} verified to exist", bucketName);
            return true;

        } catch (NoSuchBucketException e) {
            // Bucket doesn't exist - log warning but don't try to create
            // Bucket should be created via AWS Console, CLI, or Terraform
            log.warn("Bucket '{}' does not exist. Please create it via AWS Console or Terraform.", 
                    config.getBucketName());
            log.warn("The configuration will be saved but uploads will fail until the bucket is created.");
            return false;

        } catch (Exception e) {
            // Permission issues or other errors - log warning
            log.warn("Could not verify bucket '{}' existence: {}. Uploads may fail if bucket doesn't exist.", 
                    config.getBucketName(), e.getMessage());
            return true;  // Optimistic - allow configuration to be saved
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
}
