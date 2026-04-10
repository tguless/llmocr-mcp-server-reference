package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * InvoiceMetadata Entity
 * 
 * Stores customer-specific key-value pairs for invoices.
 * This allows for flexible, schema-less storage of invoice-specific data
 * without modifying the core Invoice entity or table structure.
 * 
 * Examples: generated kWh, contracted rates, custom calculations, etc.
 */
@Entity
@Table(name = "invoice_metadata", schema = "mcp_invoice",
       uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "metadata_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class InvoiceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "metadata_key", nullable = false, length = 255)
    private String key;

    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private String value;

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
}


