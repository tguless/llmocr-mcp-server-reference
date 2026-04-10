package com.llmocr.mcp.invoice.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "invoice_line_items", schema = "mcp_invoice",
       uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "line_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "invoice")
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity", precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    // ===== CATEGORIZATION FIELDS =====
    
    // Category is now stored as String (category code) to support tenant-specific custom categories from DB
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "category_confidence", precision = 5, scale = 4)
    private BigDecimal categoryConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "categorized_by", length = 20)
    private CategorizationMethod categorizedBy;

    @Column(name = "requires_review", nullable = false)
    @Builder.Default
    private Boolean requiresReview = false;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // Energy-specific fields
    @Column(name = "energy_unit", length = 20)
    private String energyUnit;  // kWh, kW, etc.

    @Column(name = "energy_quantity", precision = 15, scale = 3)
    private BigDecimal energyQuantity;

    @Column(name = "energy_rate", precision = 10, scale = 6)
    private BigDecimal energyRate;  // $/kWh

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Business methods
    public void calculateLineTotal() {
        // REMOVED: Line totals should NOT be auto-calculated
        // This preserves the actual invoice amounts as provided by users
        // If quantity × unitPrice ≠ lineTotal, that difference is intentional (rounding, taxes, discounts)
        // Users must explicitly set lineTotal - it is never recalculated
        if (quantity != null && unitPrice != null) {
            // PRESERVE: lineTotal is authoritative, do NOT override
            // To allow manual adjustments when rounding/taxes require it
        }
    }

    public void calculateTaxAmount() {
        if (lineTotal != null && taxRate != null) {
            this.taxAmount = lineTotal.multiply(taxRate);
        }
    }

    public boolean isPassThrough() {
        if (category == null) return false;
        // Category is now String, so no need for .name()
        return category.startsWith("PASS_THROUGH_") || 
               category.startsWith("CREDIT_");
    }

    public boolean isCredit() {
        return lineTotal != null && lineTotal.compareTo(BigDecimal.ZERO) < 0;
    }

    @PrePersist
    @PreUpdate
    protected void calculateAmounts() {
        // REMOVED: calculateLineTotal() was overwriting user-entered amounts
        // calculateLineTotal();  ← DELETED - this was the bug!
        calculateTaxAmount();  // Only calculate tax, not line total
    }

    // Enums
    public enum LineItemCategory {
        PASS_THROUGH_ENERGY,
        PASS_THROUGH_DEMAND,
        PASS_THROUGH_FUEL,
        PASS_THROUGH_TRANSMISSION,
        PASS_THROUGH_ENVIRONMENTAL,
        PASS_THROUGH_OTHER,
        CREDIT_GENERATION,
        CREDIT_REBATE,
        INTERNAL_ADMIN,
        INTERNAL_FEE,
        INTERNAL_TAX,
        INTERNAL_OTHER,
        REVIEW_REQUIRED
    }

    public enum CategorizationMethod {
        AI,
        RULE,
        USER,
        HYBRID
    }
}
