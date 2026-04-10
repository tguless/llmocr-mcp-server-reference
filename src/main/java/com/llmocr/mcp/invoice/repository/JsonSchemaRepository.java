package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.JsonSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for JSON Schema management
 * 
 * Provides data access methods for querying and managing
 * versioned JSON schemas per tenant.
 */
@Repository
public interface JsonSchemaRepository extends JpaRepository<JsonSchema, Long> {

    /**
     * Find all active schemas for a tenant
     */
    List<JsonSchema> findByTenantIdAndIsActiveTrueOrderBySchemaNameAscSchemaVersionDesc(String tenantId);

    /**
     * Find all schemas for a tenant (including inactive)
     */
    List<JsonSchema> findByTenantIdOrderBySchemaNameAscSchemaVersionDesc(String tenantId);

    /**
     * Find a specific schema by tenant, name, and version
     */
    Optional<JsonSchema> findByTenantIdAndSchemaNameAndSchemaVersion(
            String tenantId, 
            String schemaName, 
            String schemaVersion
    );

    /**
     * Find all versions of a schema for a tenant
     */
    List<JsonSchema> findByTenantIdAndSchemaNameOrderBySchemaVersionDesc(
            String tenantId, 
            String schemaName
    );

    /**
     * Find the latest active version of a schema
     */
    @Query("SELECT s FROM JsonSchema s WHERE s.tenantId = :tenantId " +
           "AND s.schemaName = :schemaName AND s.isActive = true " +
           "ORDER BY s.schemaVersion DESC LIMIT 1")
    Optional<JsonSchema> findLatestActiveVersion(
            @Param("tenantId") String tenantId,
            @Param("schemaName") String schemaName
    );

    /**
     * Check if a schema exists for tenant/name/version
     */
    boolean existsByTenantIdAndSchemaNameAndSchemaVersion(
            String tenantId, 
            String schemaName, 
            String schemaVersion
    );

    /**
     * Count schemas for a tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Count active schemas for a tenant
     */
    long countByTenantIdAndIsActiveTrue(String tenantId);
}

