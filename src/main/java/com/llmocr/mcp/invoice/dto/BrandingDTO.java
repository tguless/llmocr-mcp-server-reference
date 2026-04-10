package com.llmocr.mcp.invoice.dto;

import lombok.*;

/**
 * DTO for tenant branding information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandingDTO {
    
    private String tenantId;
    private String tenantName;
    private String displayName;
    private String tagline;
    private String logoUrl;
    private String loginLogoUrl;
    private String primaryColor;
    private String secondaryColor;
    
    /**
     * Get display name, falling back to tenant name if not set
     */
    public String getEffectiveDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : tenantName;
    }
    
    /**
     * Get primary color, falling back to default if not set
     */
    public String getEffectivePrimaryColor() {
        return primaryColor != null && !primaryColor.isEmpty() ? primaryColor : "#1976d2";
    }
    
    /**
     * Get secondary color, falling back to default if not set
     */
    public String getEffectiveSecondaryColor() {
        return secondaryColor != null && !secondaryColor.isEmpty() ? secondaryColor : "#42a5f5";
    }
}

