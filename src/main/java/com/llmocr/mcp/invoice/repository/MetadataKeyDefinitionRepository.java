package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MetadataKeyDefinition entity
 * 
 * Manages metadata key definitions that control which keys are valid
 * for storing invoice metadata.
 */
public interface MetadataKeyDefinitionRepository extends JpaRepository<MetadataKeyDefinition, Long> {
    
    /**
     * Find metadata key definitions by tenant and key code
     */
    Optional<MetadataKeyDefinition> findByTenantIdAndKeyCode(String tenantId, String keyCode);
    
    /**
     * Find all active metadata key definitions for a tenant
     */
    List<MetadataKeyDefinition> findByTenantIdAndActiveTrue(String tenantId);
    
    /**
     * Find all active metadata key definitions for a tenant by category
     */
    List<MetadataKeyDefinition> findByTenantIdAndActiveTrueAndCategory(String tenantId, String category);
    
    /**
     * Find all metadata key definitions for a tenant (including inactive)
     */
    List<MetadataKeyDefinition> findByTenantIdOrderByDisplayName(String tenantId);
    
    /**
     * Find all metadata key definitions for a tenant ordered by category
     */
    @Query("SELECT m FROM MetadataKeyDefinition m WHERE m.tenantId = :tenantId AND m.active = true ORDER BY m.category, m.displayName")
    List<MetadataKeyDefinition> findAllActiveByTenantOrderedByCategory(@Param("tenantId") String tenantId);
    
    /**
     * Find required metadata keys for a tenant
     */
    List<MetadataKeyDefinition> findByTenantIdAndActiveTrueAndRequiredTrue(String tenantId);
    
    /**
     * Find billable metadata keys for a tenant
     */
    List<MetadataKeyDefinition> findByTenantIdAndActiveTrueAndBillableTrue(String tenantId);
    
    /**
     * Check if a metadata key exists for a tenant
     */
    boolean existsByTenantIdAndKeyCode(String tenantId, String keyCode);

    /**
     * Find all metadata key definitions for a tenant (no ordering)
     */
    List<MetadataKeyDefinition> findByTenantId(String tenantId);

    /**
     * Count metadata key definitions for a tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Get distinct tenant IDs that have metadata key definitions
     */
    @Query("SELECT DISTINCT m.tenantId FROM MetadataKeyDefinition m ORDER BY m.tenantId")
    List<String> findDistinctTenantIds();
}
