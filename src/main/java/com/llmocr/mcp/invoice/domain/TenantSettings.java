package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Tenant-level settings for MCP Invoice Server.
 * 
 * Stores configuration such as LLMOCR API key for service-to-service communication.
 */
@Entity
@Table(name = "tenant_settings", schema = "mcp_invoice", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id"}, name = "uk_tenant_settings_tenant_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 100)
    private String tenantId;

    /**
     * LLMOCR API key for service-to-service authentication.
     * Used when calling LLMOCR endpoints to fetch S3 configurations, upload files, etc.
     */
    @Column(name = "llmocr_api_key", columnDefinition = "TEXT")
    private String llmOcrApiKey;

    /**
     * Masked version of the API key for display purposes.
     * e.g., "llmocr_svc_abc***xyz"
     */
    @Column(name = "llmocr_api_key_masked", length = 100)
    private String llmOcrApiKeyMasked;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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

    /**
     * Create a masked version of the API key for display.
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 16) {
            return "***";
        }
        String prefix = apiKey.substring(0, 12);  // e.g., "llmocr_svc_a"
        String suffix = apiKey.substring(apiKey.length() - 4);
        return prefix + "***" + suffix;
    }

    @Override
    public String toString() {
        return "TenantSettings{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", hasLlmOcrApiKey=" + (llmOcrApiKey != null && !llmOcrApiKey.isEmpty()) +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }
}

