package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    // Basic tenant-aware queries
    List<Vendor> findByTenantId(String tenantId);

    Page<Vendor> findByTenantId(String tenantId, Pageable pageable);

    Optional<Vendor> findByTenantIdAndId(String tenantId, Long id);

    Optional<Vendor> findByTenantIdAndVendorName(String tenantId, String vendorName);

    Optional<Vendor> findByTenantIdAndVendorCode(String tenantId, String vendorCode);

    // Active vendors
    List<Vendor> findByTenantIdAndActiveTrue(String tenantId);

    Page<Vendor> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    // Search queries
    @Query("SELECT v FROM Vendor v WHERE v.tenantId = :tenantId " +
           "AND (LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(v.vendorCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(v.taxId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(v.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Vendor> searchByTenantIdAndTerm(
            @Param("tenantId") String tenantId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    // Filter by category
    List<Vendor> findByTenantIdAndCategory(String tenantId, String category);

    // Filter by tax ID
    Optional<Vendor> findByTenantIdAndTaxId(String tenantId, String taxId);

    // Check existence
    boolean existsByTenantIdAndVendorName(String tenantId, String vendorName);

    boolean existsByTenantIdAndVendorCode(String tenantId, String vendorCode);

    boolean existsByTenantIdAndTaxId(String tenantId, String taxId);

    // Statistics
    @Query("SELECT COUNT(v) FROM Vendor v WHERE v.tenantId = :tenantId AND v.active = true")
    long countActiveByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(v) FROM Vendor v WHERE v.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") String tenantId);

    // Get distinct categories for a tenant
    @Query("SELECT DISTINCT v.category FROM Vendor v WHERE v.tenantId = :tenantId AND v.category IS NOT NULL")
    List<String> findDistinctCategoriesByTenantId(@Param("tenantId") String tenantId);
}



