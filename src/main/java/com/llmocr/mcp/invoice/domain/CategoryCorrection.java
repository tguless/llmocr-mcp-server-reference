package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "category_corrections", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "line_item_id", nullable = false)
    private Long lineItemId;

    @Column(name = "original_category", nullable = false, length = 50)
    private String originalCategory;

    @Column(name = "original_confidence", precision = 5, scale = 4)
    private BigDecimal originalConfidence;

    @Column(name = "corrected_category", nullable = false, length = 50)
    private String correctedCategory;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "line_item_description", columnDefinition = "TEXT")
    private String lineItemDescription;

    @Column(name = "line_item_amount", precision = 15, scale = 2)
    private BigDecimal lineItemAmount;

    @Column(name = "corrected_by", nullable = false, length = 100)
    private String correctedBy;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false)
    private LocalDateTime correctedAt;
}

