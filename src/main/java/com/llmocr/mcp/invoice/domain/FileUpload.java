package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks all file uploads in the invoice system
 * Each upload gets a unique ID that can be included in the generated filename
 */
@Entity
@Table(name = "file_uploads", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "generated_filename", nullable = false, length = 300, unique = true)
    private String generatedFilename;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "s3_bucket_name", length = 100)
    private String s3BucketName;

    @Column(name = "s3_object_key", length = 500)
    private String s3ObjectKey;

    @Column(name = "upload_source", length = 50)
    private String uploadSource; // WEB_UI, S3_AUTO, API, etc.

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "job_id")
    private Long jobId; // PDF processing job ID from main app

    @Column(name = "invoice_id")
    private Long invoiceId; // Associated invoice (if created)

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public enum ProcessingStatus {
        UPLOADED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

