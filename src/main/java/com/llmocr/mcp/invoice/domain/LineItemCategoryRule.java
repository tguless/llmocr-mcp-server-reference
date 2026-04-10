package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "line_item_category_rules", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineItemCategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Rule matching criteria
    @Column(name = "keyword_pattern", length = 500)
    private String keywordPattern;

    @Column(name = "amount_min", precision = 15, scale = 2)
    private BigDecimal amountMin;

    @Column(name = "amount_max", precision = 15, scale = 2)
    private BigDecimal amountMax;

    @Column(name = "is_credit")
    private Boolean isCredit;

    // Target category
    // Target category is now stored as String (category code) to support tenant-specific custom categories
    @Column(name = "target_category", nullable = false, length = 50)
    private String targetCategory;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Learning metrics
    @Column(name = "match_count", nullable = false)
    @Builder.Default
    private Integer matchCount = 0;

    @Column(name = "correction_count", nullable = false)
    @Builder.Default
    private Integer correctionCount = 0;

    @Column(name = "accuracy_rate", precision = 5, scale = 4)
    private BigDecimal accuracyRate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // Business methods
    public void incrementMatchCount() {
        this.matchCount++;
        recalculateAccuracy();
    }

    public void incrementCorrectionCount() {
        this.correctionCount++;
        recalculateAccuracy();
    }

    private void recalculateAccuracy() {
        if (matchCount > 0) {
            int correct = matchCount - correctionCount;
            this.accuracyRate = new BigDecimal(correct)
                    .divide(new BigDecimal(matchCount), 4, RoundingMode.HALF_UP);
        }
    }
}

