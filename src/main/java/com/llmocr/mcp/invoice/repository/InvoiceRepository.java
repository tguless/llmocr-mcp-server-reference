package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Basic tenant-aware queries
    List<Invoice> findByTenantId(String tenantId);

    Page<Invoice> findByTenantId(String tenantId, Pageable pageable);

    Optional<Invoice> findByTenantIdAndId(String tenantId, Long id);

    Optional<Invoice> findByTenantIdAndInvoiceNumber(String tenantId, String invoiceNumber);

    // Duplicate detection queries
    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.vendorName = :vendorName " +
           "AND i.totalAmount = :totalAmount " +
           "AND i.invoiceDate = :invoiceDate")
    List<Invoice> findPotentialDuplicates(
            @Param("tenantId") String tenantId,
            @Param("vendorName") String vendorName,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("invoiceDate") LocalDate invoiceDate);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.invoiceNumber = :invoiceNumber " +
           "AND i.vendorName = :vendorName")
    List<Invoice> findByTenantIdAndInvoiceNumberAndVendorName(
            @Param("tenantId") String tenantId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("vendorName") String vendorName);

    // Status-based queries
    List<Invoice> findByTenantIdAndStatus(String tenantId, Invoice.InvoiceStatus status);

    List<Invoice> findByTenantIdAndProcessingStatus(String tenantId, Invoice.ProcessingStatus processingStatus);

    // Vendor-based queries
    List<Invoice> findByTenantIdAndVendorName(String tenantId, String vendorName);

    Page<Invoice> findByTenantIdAndVendorNameContainingIgnoreCase(
            String tenantId, String vendorName, Pageable pageable);

    // Date-based queries
    List<Invoice> findByTenantIdAndInvoiceDateBetween(
            String tenantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.invoiceDate >= :startDate " +
           "AND i.invoiceDate <= :endDate " +
           "ORDER BY i.invoiceDate DESC")
    Page<Invoice> findByTenantIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    // Amount-based queries
    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.totalAmount >= :minAmount " +
           "AND i.totalAmount <= :maxAmount")
    List<Invoice> findByTenantIdAndAmountRange(
            @Param("tenantId") String tenantId,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);

    // Statistics queries
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.status = :status")
    BigDecimal sumTotalAmountByTenantIdAndStatus(
            @Param("tenantId") String tenantId,
            @Param("status") Invoice.InvoiceStatus status);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.processingStatus = :processingStatus")
    long countByTenantIdAndProcessingStatus(
            @Param("tenantId") String tenantId,
            @Param("processingStatus") Invoice.ProcessingStatus processingStatus);

    // Search queries
    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND (LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(i.vendorName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(i.customerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Invoice> searchByTenantIdAndTerm(
            @Param("tenantId") String tenantId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    // Recent invoices
    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
           "ORDER BY i.createdAt DESC")
    Page<Invoice> findRecentByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    // Validation queries
    boolean existsByTenantIdAndInvoiceNumber(String tenantId, String invoiceNumber);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END " +
           "FROM Invoice i WHERE i.tenantId = :tenantId " +
           "AND i.invoiceNumber = :invoiceNumber " +
           "AND i.id != :excludeId")
    boolean existsByTenantIdAndInvoiceNumberAndIdNot(
            @Param("tenantId") String tenantId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("excludeId") Long excludeId);
}
