package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {

    /**
     * Find file upload by generated filename
     */
    Optional<FileUpload> findByGeneratedFilename(String generatedFilename);

    /**
     * Find file upload by tenant and original filename (for checking if already uploaded)
     */
    Optional<FileUpload> findByTenantIdAndOriginalFilename(String tenantId, String originalFilename);

    /**
     * Find uploads by tenant and job ID ordered by newest first.
     */
    List<FileUpload> findByTenantIdAndJobIdOrderByCreatedAtDesc(String tenantId, Long jobId);

    /**
     * Find uploads by tenant and exact S3 location ordered by newest first.
     */
    List<FileUpload> findByTenantIdAndS3BucketNameAndS3ObjectKeyOrderByCreatedAtDesc(
            String tenantId, String s3BucketName, String s3ObjectKey);

    /**
     * Find uploads by tenant and original filename ordered by newest first.
     */
    List<FileUpload> findByTenantIdAndOriginalFilenameOrderByCreatedAtDesc(String tenantId, String originalFilename);

    /**
     * Find all uploads for a tenant
     */
    List<FileUpload> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find upload by job ID
     */
    Optional<FileUpload> findByJobId(Long jobId);

    /**
     * Find upload by invoice ID
     */
    Optional<FileUpload> findByInvoiceId(Long invoiceId);

    /**
     * Find uploads by tenant and processing status
     */
    List<FileUpload> findByTenantIdAndProcessingStatusOrderByCreatedAtDesc(
            String tenantId, FileUpload.ProcessingStatus status);

    /**
     * Count uploads by tenant
     */
    @Query("SELECT COUNT(f) FROM FileUpload f WHERE f.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") String tenantId);
}

