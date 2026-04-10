package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.PdfRawJsonProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PDF Raw JSON Processing records
 * 
 * Provides data access methods for querying raw JSON processing
 * paired with PDF files.
 */
@Repository
public interface PdfRawJsonProcessingRepository extends JpaRepository<PdfRawJsonProcessing, Long> {

    /**
     * Find all processing records for a specific tenant
     */
    List<PdfRawJsonProcessing> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find all processing records for a specific job
     */
    List<PdfRawJsonProcessing> findByJobIdOrderByCreatedAtDesc(String jobId);

    /**
     * Find all processing records for a specific tenant and job
     */
    List<PdfRawJsonProcessing> findByTenantIdAndJobIdOrderByCreatedAtDesc(String tenantId, String jobId);

    /**
     * Find processing records for a specific tenant and source filename
     */
    List<PdfRawJsonProcessing> findByTenantIdAndSourceFilenameOrderByCreatedAtDesc(String tenantId, String sourceFilename);

    /**
     * Find the most recent processing record for a specific job
     */
    Optional<PdfRawJsonProcessing> findFirstByJobIdOrderByCreatedAtDesc(String jobId);

    /**
     * Find all processing records for a specific tenant and tool
     */
    List<PdfRawJsonProcessing> findByTenantIdAndToolNameOrderByCreatedAtDesc(String tenantId, String toolName);

    /**
     * Count processing records for a specific job
     */
    long countByJobId(String jobId);

    /**
     * Count processing records for a specific tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Find processing records where schema was missing
     */
    @Query("SELECT p FROM PdfRawJsonProcessing p WHERE p.tenantId = :tenantId AND p.processingStatus = 'SCHEMA_MISSING' ORDER BY p.createdAt DESC")
    List<PdfRawJsonProcessing> findSchemaMissingRecords(@Param("tenantId") String tenantId);

    /**
     * Find processing records with errors
     */
    @Query("SELECT p FROM PdfRawJsonProcessing p WHERE p.tenantId = :tenantId AND p.processingStatus = 'ERROR' ORDER BY p.createdAt DESC")
    List<PdfRawJsonProcessing> findErrorRecords(@Param("tenantId") String tenantId);
}

