package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

    List<InvoiceLineItem> findByInvoiceIdOrderByLineNumber(Long invoiceId);

    @Query("SELECT li FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId " +
           "AND li.invoice.tenantId = :tenantId ORDER BY li.lineNumber")
    List<InvoiceLineItem> findByInvoiceIdAndTenantIdOrderByLineNumber(
            @Param("invoiceId") Long invoiceId,
            @Param("tenantId") String tenantId);

    @Query("SELECT COUNT(li) FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId")
    long countByInvoiceId(@Param("invoiceId") Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);

    // ===== NEW METHODS FOR CATEGORIZATION =====

    /**
     * Find line items by invoice and category (category is now String code)
     */
    @Query("SELECT li FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId AND li.category = :category")
    List<InvoiceLineItem> findByInvoiceIdAndCategory(@Param("invoiceId") Long invoiceId, 
                                                      @Param("category") String category);

    /**
     * Find line items requiring review
     */
    @Query("SELECT li FROM InvoiceLineItem li JOIN li.invoice i WHERE i.tenantId = :tenantId AND li.requiresReview = :requiresReview")
    List<InvoiceLineItem> findByTenantIdAndRequiresReview(@Param("tenantId") String tenantId, @Param("requiresReview") boolean requiresReview);

    /**
     * Find pass-through line items for an invoice (categories are now String codes)
     */
    @Query("SELECT li FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId AND li.category IN :categories")
    List<InvoiceLineItem> findPassThroughItems(@Param("invoiceId") Long invoiceId, 
                                                @Param("categories") List<String> categories);

    /**
     * Sum line totals by category for a tenant (category is now String code)
     */
    @Query("SELECT COALESCE(SUM(li.lineTotal), 0) FROM InvoiceLineItem li JOIN li.invoice i " +
           "WHERE i.tenantId = :tenantId AND li.category = :category")
    BigDecimal sumLineTotalsByCategory(@Param("tenantId") String tenantId, 
                                       @Param("category") String category);

    /**
     * Count line items by category for a tenant (category is now String code)
     */
    @Query("SELECT COUNT(li) FROM InvoiceLineItem li JOIN li.invoice i " +
           "WHERE i.tenantId = :tenantId AND li.category = :category")
    long countByTenantIdAndCategory(@Param("tenantId") String tenantId, 
                                    @Param("category") String category);

    /**
     * Find line items with low confidence scores
     */
    @Query("SELECT li FROM InvoiceLineItem li JOIN li.invoice i " +
           "WHERE i.tenantId = :tenantId AND li.categoryConfidence < :minConfidence")
    List<InvoiceLineItem> findLowConfidenceItems(@Param("tenantId") String tenantId, 
                                                  @Param("minConfidence") BigDecimal minConfidence);

    /**
     * Find line items by tenant and category (category is now String code)
     */
    @Query("SELECT li FROM InvoiceLineItem li JOIN li.invoice i " +
           "WHERE i.tenantId = :tenantId AND li.category = :category")
    List<InvoiceLineItem> findByTenantIdAndCategory(@Param("tenantId") String tenantId, 
                                                     @Param("category") String category);

    /**
     * Find all line items for a tenant, ordered by creation date
     */
    @Query("SELECT li FROM InvoiceLineItem li JOIN li.invoice i " +
           "WHERE i.tenantId = :tenantId ORDER BY li.createdAt DESC")
    List<InvoiceLineItem> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") String tenantId);

    /**
     * Find line items by invoice ID (without tenant filter)
     */
    List<InvoiceLineItem> findByInvoiceId(Long invoiceId);
    
    /**
     * Find a specific line item by invoice ID and line number
     */
    @Query("SELECT li FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId AND li.lineNumber = :lineNumber")
    java.util.Optional<InvoiceLineItem> findByInvoiceIdAndLineNumber(@Param("invoiceId") Long invoiceId, 
                                                                      @Param("lineNumber") Integer lineNumber);
    
    /**
     * Find the maximum line number for an invoice (for auto-generating sequential line numbers)
     */
    @Query("SELECT MAX(li.lineNumber) FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId")
    java.util.Optional<Integer> findMaxLineNumberByInvoiceId(@Param("invoiceId") Long invoiceId);
}
