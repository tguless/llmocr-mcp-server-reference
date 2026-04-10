package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.dto.BrandingDTO;
import com.llmocr.mcp.invoice.service.BrandingService;
import com.llmocr.mcp.invoice.service.S3LogoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant branding information
 * Provides public endpoint for branding data (used by login page, etc.)
 * Also provides logo proxy endpoint
 */
@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
@Slf4j
public class BrandingController {

    private final BrandingService brandingService;
    private final S3LogoService logoService;

    /**
     * Get branding information for a specific tenant (public endpoint)
     * @param tenantId the tenant identifier
     * @return branding information
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<BrandingDTO> getBrandingByTenantId(@PathVariable String tenantId) {
        log.info("Fetching branding for tenant: {}", tenantId);
        
        BrandingDTO branding = brandingService.getBrandingByTenantId(tenantId);
        
        if (branding == null) {
            log.warn("No branding found for tenant: {}, returning default", tenantId);
            branding = brandingService.getDefaultBranding();
        }
        
        return ResponseEntity.ok(branding);
    }

    /**
     * Get default branding information (public endpoint)
     * Used when no tenant is specified or tenant not found
     * @return default branding information
     */
    @GetMapping("/default")
    public ResponseEntity<BrandingDTO> getDefaultBranding() {
        log.info("Fetching default branding");
        BrandingDTO branding = brandingService.getDefaultBranding();
        return ResponseEntity.ok(branding);
    }

    /**
     * Proxy endpoint for logo files (public endpoint)
     * Retrieves logos from S3 and serves them through the backend
     * 
     * @param logoPath S3 object key path (e.g., "logos/admin/main-uuid.png")
     * @return Logo file with appropriate content type
     */
    @GetMapping("/logo/**")
    public ResponseEntity<byte[]> getLogoProxy(@RequestParam(required = false) String tenantId,
                                                HttpServletRequest request) {
        try {
            // Extract the full path after /api/branding/logo/
            String requestUri = request.getRequestURI();
            String logoPath = requestUri.substring(requestUri.indexOf("/logo/") + 6);
            
            log.debug("Proxying logo request: {}", logoPath);

            // Extract tenant ID from path if not provided (logos/TENANT_ID/...)
            String effectiveTenantId = tenantId;
            if (effectiveTenantId == null) {
                String[] pathParts = logoPath.split("/");
                if (pathParts.length >= 2) {
                    effectiveTenantId = pathParts[1]; // logos/TENANT_ID/...
                } else {
                    effectiveTenantId = "admin"; // fallback to admin tenant
                }
            }

            // Retrieve logo from S3
            byte[] logoBytes = logoService.retrieveLogo(effectiveTenantId, logoPath);

            // Determine content type from file extension
            String contentType = logoService.getLogoContentType(effectiveTenantId, logoPath);

            // Set cache headers for better performance (cache for 1 hour)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setCacheControl("public, max-age=3600");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(logoBytes);

        } catch (Exception e) {
            log.error("Failed to proxy logo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

