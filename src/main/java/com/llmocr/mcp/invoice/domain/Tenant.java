package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 100)
    private String tenantId;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

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

    // Branding fields
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;  // S3 object key for main logo

    @Column(name = "login_logo_url", length = 1000)
    private String loginLogoUrl;  // S3 object key for login logo

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "tagline", length = 500)
    private String tagline;

    // Validation configuration
    @Column(name = "service_period_required", nullable = false)
    @Builder.Default
    private Boolean servicePeriodRequired = true;

    @PrePersist
    protected void onCreate() {
        if (active == null) {
            active = true;
        }
        if (servicePeriodRequired == null) {
            servicePeriodRequired = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
