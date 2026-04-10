package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for S3 Bucket Configuration entities
 * 
 * Provides database access for storing and retrieving S3/MinIO bucket configurations.
 */
@Repository
public interface S3BucketConfigurationRepository extends JpaRepository<S3BucketConfiguration, Long> {

    /**
     * Find all active bucket configurations for a tenant
     */
    List<S3BucketConfiguration> findByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Find all active bucket configurations for a tenant, ordered by bucket name
     */
    List<S3BucketConfiguration> findByTenantIdAndIsActiveTrueOrderByBucketNameAsc(String tenantId);

    /**
     * Find a specific bucket configuration by tenant and bucket name
     */
    Optional<S3BucketConfiguration> findByTenantIdAndBucketNameAndIsActiveTrue(String tenantId, String bucketName);

    /**
     * Find all configurations (active or inactive) for a tenant
     */
    List<S3BucketConfiguration> findByTenantId(String tenantId);

    /**
     * Find a bucket configuration by ID and verify it belongs to the tenant
     */
    Optional<S3BucketConfiguration> findByIdAndTenantId(Long id, String tenantId);
    
    /**
     * SECURITY: Find all active configurations for a bucket across ALL tenants
     * Used to prevent bucket name conflicts between tenants
     */
    List<S3BucketConfiguration> findByBucketNameAndIsActiveTrue(String bucketName);
}
