package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Tenant;
import com.llmocr.mcp.invoice.dto.BrandingDTO;
import com.llmocr.mcp.invoice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tenant branding
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandingService {

    private final TenantRepository tenantRepository;

    /**
     * Get branding information for a specific tenant
     * @param tenantId the tenant identifier
     * @return branding DTO or null if tenant not found
     */
    @Transactional(readOnly = true)
    public BrandingDTO getBrandingByTenantId(String tenantId) {
        return tenantRepository.findByTenantIdAndActiveTrue(tenantId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    /**
     * Get default branding (for admin tenant or system default)
     * @return default branding DTO
     */
    @Transactional(readOnly = true)
    public BrandingDTO getDefaultBranding() {
        // Try to get admin tenant branding first
        return tenantRepository.findByTenantIdAndActiveTrue("admin")
                .map(this::convertToDTO)
                .orElseGet(() -> {
                    // Fallback to hardcoded defaults if admin tenant not found
                    log.warn("Admin tenant not found, using hardcoded defaults");
                    return BrandingDTO.builder()
                            .tenantId("default")
                            .tenantName("Invoice Management System")
                            .displayName("Invoice Management System")
                            .tagline("AI-Powered Document Processing")
                            .logoUrl("/image.png")
                            .loginLogoUrl("/image.png")
                            .primaryColor("#1976d2")
                            .secondaryColor("#42a5f5")
                            .build();
                });
    }

    /**
     * Convert Tenant entity to BrandingDTO
     */
    private BrandingDTO convertToDTO(Tenant tenant) {
        return BrandingDTO.builder()
                .tenantId(tenant.getTenantId())
                .tenantName(tenant.getTenantName())
                .displayName(tenant.getDisplayName())
                .tagline(tenant.getTagline())
                .logoUrl(tenant.getLogoUrl())
                .loginLogoUrl(tenant.getLoginLogoUrl())
                .primaryColor(tenant.getPrimaryColor())
                .secondaryColor(tenant.getSecondaryColor())
                .build();
    }

    /**
     * Update branding for a tenant
     * @param tenantId the tenant identifier
     * @param brandingDTO the branding information to update
     * @param updatedBy user making the update
     * @return updated branding DTO
     */
    @Transactional
    public BrandingDTO updateBranding(String tenantId, BrandingDTO brandingDTO, String updatedBy) {
        Tenant tenant = tenantRepository.findByTenantIdAndActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Update branding fields (only if provided)
        if (brandingDTO.getDisplayName() != null) {
            tenant.setDisplayName(brandingDTO.getDisplayName());
        }
        if (brandingDTO.getTagline() != null) {
            tenant.setTagline(brandingDTO.getTagline());
        }
        if (brandingDTO.getLogoUrl() != null) {
            tenant.setLogoUrl(brandingDTO.getLogoUrl());
        }
        if (brandingDTO.getLoginLogoUrl() != null) {
            tenant.setLoginLogoUrl(brandingDTO.getLoginLogoUrl());
        }
        if (brandingDTO.getPrimaryColor() != null) {
            tenant.setPrimaryColor(brandingDTO.getPrimaryColor());
        }
        if (brandingDTO.getSecondaryColor() != null) {
            tenant.setSecondaryColor(brandingDTO.getSecondaryColor());
        }

        tenant.setUpdatedBy(updatedBy);

        Tenant saved = tenantRepository.save(tenant);
        log.info("Updated branding for tenant: {} by user: {}", tenantId, updatedBy);

        return convertToDTO(saved);
    }
}

