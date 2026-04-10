package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.LineItemCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LineItemCategoryRuleRepository extends JpaRepository<LineItemCategoryRule, Long> {

    /**
     * Find active rules for a tenant, ordered by priority (highest first)
     */
    @Query("SELECT r FROM LineItemCategoryRule r WHERE r.tenantId = :tenantId AND r.isActive = true ORDER BY r.priority DESC")
    List<LineItemCategoryRule> findActiveRulesByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find rules by tenant and active status
     */
    List<LineItemCategoryRule> findByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Find rules with high accuracy rates
     */
    @Query("SELECT r FROM LineItemCategoryRule r WHERE r.tenantId = :tenantId AND r.accuracyRate >= :minAccuracy ORDER BY r.accuracyRate DESC")
    List<LineItemCategoryRule> findHighAccuracyRules(@Param("tenantId") String tenantId, 
                                                      @Param("minAccuracy") BigDecimal minAccuracy);
}

