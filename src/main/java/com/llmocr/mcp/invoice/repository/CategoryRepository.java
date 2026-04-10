package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find all categories for a tenant
     */
    List<Category> findByTenantIdOrderByDisplayOrderAscCategoryNameAsc(String tenantId);

    /**
     * Find active categories for a tenant
     */
    List<Category> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCategoryNameAsc(String tenantId);

    /**
     * Find a specific category by tenant and code
     */
    Optional<Category> findByTenantIdAndCategoryCode(String tenantId, String categoryCode);

    /**
     * Check if a category exists for a tenant
     */
    boolean existsByTenantIdAndCategoryCode(String tenantId, String categoryCode);

    /**
     * Find pass-through categories for a tenant
     */
    List<Category> findByTenantIdAndIsPassThroughTrueAndIsActiveTrueOrderByDisplayOrderAsc(String tenantId);

    /**
     * Find credit categories for a tenant
     */
    List<Category> findByTenantIdAndIsCreditTrueAndIsActiveTrueOrderByDisplayOrderAsc(String tenantId);

    /**
     * Find billable categories for a tenant
     */
    List<Category> findByTenantIdAndIsBillableTrueAndIsActiveTrueOrderByDisplayOrderAsc(String tenantId);

    /**
     * Search categories by name or description
     */
    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.isActive = true " +
           "AND (LOWER(c.categoryName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Category> searchCategories(@Param("tenantId") String tenantId, @Param("searchTerm") String searchTerm);

    /**
     * Count active categories for a tenant
     */
    long countByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Find all active categories for a tenant (without ordering)
     */
    List<Category> findByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Get distinct tenant IDs that have categories
     */
    @Query("SELECT DISTINCT c.tenantId FROM Category c ORDER BY c.tenantId")
    List<String> findDistinctTenantIds();
}

