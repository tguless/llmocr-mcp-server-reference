package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * S3 Bucket Configuration for MCP Invoice Server
 * 
 * Stores connection details for S3/MinIO buckets per tenant.
 * Allows admin UI to retrieve and display invoice PDFs from configured buckets.
 */
@Entity
@Table(name = "s3_bucket_configurations", schema = "mcp_invoice", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "bucket_name"}, name = "uk_s3_bucket_per_tenant")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3BucketConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "bucket_name", nullable = false, length = 63)
    private String bucketName;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;  // e.g., "http://minio:9000"

    @Column(name = "access_key_id", nullable = false, length = 200)
    private String accessKeyId;

    @Column(name = "secret_access_key", nullable = false, columnDefinition = "TEXT")
    private String secretAccessKey;

    @Column(name = "region", length = 50)
    private String region;  // e.g., "us-east-1", defaults to "us-east-1"

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Override
    public String toString() {
        return "S3BucketConfiguration{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", bucketName='" + bucketName + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", region='" + region + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }
}
