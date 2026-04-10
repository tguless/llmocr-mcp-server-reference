package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.CategoryCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryCorrectionRepository extends JpaRepository<CategoryCorrection, Long> {

    /**
     * Find corrections for a tenant
     */
    List<CategoryCorrection> findByTenantIdOrderByCorrectedAtDesc(String tenantId);

    /**
     * Count corrections by original category
     */
    @Query("SELECT c.originalCategory, COUNT(c) FROM CategoryCorrection c WHERE c.tenantId = :tenantId GROUP BY c.originalCategory")
    List<Object[]> countByOriginalCategory(@Param("tenantId") String tenantId);

    /**
     * Find corrections for learning
     */
    @Query("SELECT c FROM CategoryCorrection c WHERE c.tenantId = :tenantId AND c.lineItemDescription LIKE %:keyword%")
    List<CategoryCorrection> findByTenantIdAndKeyword(@Param("tenantId") String tenantId, 
                                                       @Param("keyword") String keyword);
}

