package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Tenant-specific invoice line item category
 * Replaces the hardcoded enum to allow per-tenant category customization
 */
@Entity
@Table(name = "categories", schema = "mcp_invoice",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "category_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_pass_through", nullable = false)
    @Builder.Default
    private Boolean isPassThrough = false;

    @Column(name = "is_credit", nullable = false)
    @Builder.Default
    private Boolean isCredit = false;

    @Column(name = "is_billable", nullable = false)
    @Builder.Default
    private Boolean isBillable = false;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords; // Comma-separated list

    @Column(name = "examples", columnDefinition = "TEXT")
    private String examples; // Comma-separated list

    @Column(name = "categorization_instructions", columnDefinition = "TEXT")
    private String categorizationInstructions;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = false;

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

    @PrePersist
    protected void onCreate() {
        if (isPassThrough == null) {
            isPassThrough = false;
        }
        if (isCredit == null) {
            isCredit = false;
        }
        if (isBillable == null) {
            isBillable = false;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (required == null) {
            required = false;
        }
    }
}

