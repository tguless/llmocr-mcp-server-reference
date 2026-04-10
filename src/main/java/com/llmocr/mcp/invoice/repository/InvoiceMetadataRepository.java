package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.InvoiceMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for InvoiceMetadata - Key-Value pairs for invoices
 * 
 * Supports tenant-aware operations for storing customer-specific metadata
 * that should not be part of the core invoice schema.
 */
@Repository
public interface InvoiceMetadataRepository extends JpaRepository<InvoiceMetadata, Long> {

    /**
     * Find metadata by tenant ID and invoice ID
     */
    List<InvoiceMetadata> findByTenantIdAndInvoiceId(String tenantId, Long invoiceId);

    /**
     * Find specific metadata value by tenant ID, invoice ID, and key
     */
    Optional<InvoiceMetadata> findByTenantIdAndInvoiceIdAndKey(String tenantId, Long invoiceId, String key);

    /**
     * Find all metadata for a specific key across invoices
     */
    List<InvoiceMetadata> findByTenantIdAndKey(String tenantId, String key);

    /**
     * Check if metadata key exists for an invoice
     */
    boolean existsByTenantIdAndInvoiceIdAndKey(String tenantId, Long invoiceId, String key);

    /**
     * Delete metadata by tenant ID, invoice ID, and key
     */
    void deleteByTenantIdAndInvoiceIdAndKey(String tenantId, Long invoiceId, String key);

    /**
     * Delete all metadata for an invoice (useful for invoice deletion)
     */
    void deleteByTenantIdAndInvoiceId(String tenantId, Long invoiceId);

    /**
     * Count metadata entries by tenant ID and invoice ID
     */
    long countByTenantIdAndInvoiceId(String tenantId, Long invoiceId);

    /**
     * Query to find multiple keys for an invoice efficiently
     */
    @Query("SELECT im FROM InvoiceMetadata im WHERE im.tenantId = :tenantId " +
           "AND im.invoiceId = :invoiceId AND im.key IN :keys")
    List<InvoiceMetadata> findByTenantIdAndInvoiceIdAndKeyIn(
            @Param("tenantId") String tenantId,
            @Param("invoiceId") Long invoiceId,
            @Param("keys") List<String> keys);

    /**
     * Find all metadata entries for a tenant (for audit/export purposes)
     */
    List<InvoiceMetadata> findByTenantId(String tenantId);
}
