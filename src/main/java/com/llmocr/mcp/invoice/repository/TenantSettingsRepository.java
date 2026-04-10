package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for tenant settings.
 */
@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {

    /**
     * Find settings by tenant ID.
     */
    Optional<TenantSettings> findByTenantId(String tenantId);

    /**
     * Check if settings exist for a tenant.
     */
    boolean existsByTenantId(String tenantId);
}

