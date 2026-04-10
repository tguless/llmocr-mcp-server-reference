package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a JSON Schema definition
 * 
 * Stores versioned JSON schemas for validating raw JSON payloads.
 * Each tenant can define their own schemas with versioning support.
 */
@Entity
@Table(name = "json_schemas", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_json_schemas_tenant_name_version", 
                           columnNames = {"tenant_id", "schema_name", "schema_version"})
       },
       indexes = {
           @Index(name = "idx_json_schemas_tenant_id", columnList = "tenant_id"),
           @Index(name = "idx_json_schemas_name", columnList = "schema_name"),
           @Index(name = "idx_json_schemas_active", columnList = "is_active"),
           @Index(name = "idx_json_schemas_tenant_name", columnList = "tenant_id, schema_name")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class JsonSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant ID - schemas are isolated per tenant
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * Schema name (e.g., "invoice", "contract", "receipt")
     */
    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    /**
     * Schema version (e.g., "1.0", "2.0", "2024-12-02")
     */
    @Column(name = "schema_version", nullable = false, length = 50)
    private String schemaVersion;

    /**
     * Human-readable description of what this schema validates
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * The JSON Schema definition in JSON Schema Draft 2020-12 format
     * See: https://json-schema.org/
     */
    @Column(name = "schema_definition", columnDefinition = "TEXT", nullable = false)
    private String schemaDefinition;

    /**
     * Whether this schema version is active
     * Allows deprecation of old schemas without deletion
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * User who created this schema
     */
    @Column(name = "created_by")
    private String createdBy;

    /**
     * Timestamp when schema was created
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when schema was last updated
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

