package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing raw JSON processing paired with PDF files
 * 
 * Stores the relationship between:
 * - Raw JSON input sent to llmOcrRawJson tools
 * - The PDF being processed (job context)
 * - The tenant making the request
 * - The paired schema used (if any)
 * - Processing results
 */
@Entity
@Table(name = "pdf_raw_json_processing", indexes = {
    @Index(name = "idx_pdf_raw_json_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_pdf_raw_json_job_id", columnList = "job_id"),
    @Index(name = "idx_pdf_raw_json_source_filename", columnList = "source_filename"),
    @Index(name = "idx_pdf_raw_json_tenant_job", columnList = "tenant_id, job_id"),
    @Index(name = "idx_pdf_raw_json_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PdfRawJsonProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant ID from JWT token
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * User ID from JWT token
     */
    @Column(name = "user_id")
    private String userId;

    /**
     * Job ID from X-Job-ID header
     * Links this raw JSON processing to a specific PDF job
     */
    @Column(name = "job_id")
    private String jobId;

    /**
     * Source filename from X-Source-Filename header
     */
    @Column(name = "source_filename", length = 500)
    private String sourceFilename;

    /**
     * S3 bucket from X-S3-Bucket header
     */
    @Column(name = "s3_bucket")
    private String s3Bucket;

    /**
     * S3 object key from X-S3-Object-Key header
     */
    @Column(name = "s3_object_key", length = 1000)
    private String s3ObjectKey;

    /**
     * Name of the MCP tool invoked (e.g., llmOcrRawJsonTest)
     */
    @Column(name = "tool_name", nullable = false)
    private String toolName;

    /**
     * Raw JSON input sent to the tool
     */
    @Column(name = "raw_json_input", columnDefinition = "TEXT", nullable = false)
    private String rawJsonInput;

    /**
     * Processed output/result from the tool
     */
    @Column(name = "processed_output", columnDefinition = "TEXT")
    private String processedOutput;

    /**
     * The paired JSON schema that was sent in the X-JSON-Schema header
     */
    @Column(name = "paired_schema_used", columnDefinition = "TEXT")
    private String pairedSchemaUsed;

    /**
     * The name of the paired schema sent in the X-JSON-Schema-Name header
     * This prevents LLM hallucination by providing a fixed schema name in tool signatures
     */
    @Column(name = "paired_schema_name", length = 255)
    private String pairedSchemaName;

    /**
     * Reference to the JSON Schema this record was validated against
     * Many raw JSON processing records can reference one schema
     * JsonIgnore prevents Hibernate proxy serialization errors
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "json_schema_id", foreignKey = @ForeignKey(name = "fk_pdf_raw_json_processing_json_schema"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private JsonSchema jsonSchema;

    /**
     * Detailed validation errors if JSON failed schema validation
     * Stored as JSON array of error objects
     */
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    private ProcessingStatus processingStatus;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Timestamp when record was created
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when record was last updated
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Processing status enum
     */
    public enum ProcessingStatus {
        SUCCESS,
        ERROR,
        PROCESSING,
        SCHEMA_MISSING
    }
}

