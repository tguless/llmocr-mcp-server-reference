package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.dto.BrandingDTO;
import com.llmocr.mcp.invoice.service.BrandingService;
import com.llmocr.mcp.invoice.service.S3LogoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin controller for managing tenant branding
 * Handles logo uploads, branding updates, and configuration
 */
@RestController
@RequestMapping("/api/admin/branding")
@RequiredArgsConstructor
@Slf4j
public class AdminBrandingController {

    private final BrandingService brandingService;
    private final S3LogoService logoService;

    /**
     * Get current tenant's branding
     */
    @GetMapping
    public ResponseEntity<BrandingDTO> getCurrentBranding(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        log.info("Admin: Getting branding for tenant: {}", tenantId);

        BrandingDTO branding = brandingService.getBrandingByTenantId(tenantId);
        if (branding == null) {
            branding = brandingService.getDefaultBranding();
        }

        return ResponseEntity.ok(branding);
    }

    /**
     * Upload main logo
     */
    @PostMapping(value = "/logo/main", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMainLogo(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("Admin: Uploading main logo for tenant: {}", tenantId);

        try {
            // Upload to S3
            String s3Key = logoService.uploadLogo(tenantId, file, "main");

            // Update tenant branding with S3 key
            BrandingDTO brandingUpdate = BrandingDTO.builder()
                    .logoUrl(s3Key)
                    .build();
            
            BrandingDTO updated = brandingService.updateBranding(tenantId, brandingUpdate, updatedBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Main logo uploaded successfully");
            response.put("s3Key", s3Key);
            response.put("logoUrl", "/api/branding/logo/" + s3Key);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to upload main logo for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Upload login logo
     */
    @PostMapping(value = "/logo/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadLoginLogo(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("Admin: Uploading login logo for tenant: {}", tenantId);

        try {
            // Upload to S3
            String s3Key = logoService.uploadLogo(tenantId, file, "login");

            // Update tenant branding with S3 key
            BrandingDTO brandingUpdate = BrandingDTO.builder()
                    .loginLogoUrl(s3Key)
                    .build();
            
            BrandingDTO updated = brandingService.updateBranding(tenantId, brandingUpdate, updatedBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Login logo uploaded successfully");
            response.put("s3Key", s3Key);
            response.put("logoUrl", "/api/branding/logo/" + s3Key);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to upload login logo for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update branding settings (colors, display name, tagline)
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateBrandingSettings(
            @RequestBody BrandingDTO brandingUpdate,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("Admin: Updating branding settings for tenant: {}", tenantId);

        try {
            BrandingDTO updated = brandingService.updateBranding(tenantId, brandingUpdate, updatedBy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Branding updated successfully");
            response.put("branding", updated);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update branding for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Delete main logo
     */
    @DeleteMapping("/logo/main")
    public ResponseEntity<Map<String, Object>> deleteMainLogo(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("Admin: Deleting main logo for tenant: {}", tenantId);

        try {
            BrandingDTO current = brandingService.getBrandingByTenantId(tenantId);
            
            if (current != null && current.getLogoUrl() != null) {
                // Delete from S3
                logoService.deleteLogo(tenantId, current.getLogoUrl());

                // Update tenant branding
                BrandingDTO brandingUpdate = BrandingDTO.builder()
                        .logoUrl(null)
                        .build();
                brandingService.updateBranding(tenantId, brandingUpdate, updatedBy);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Main logo deleted successfully");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete main logo for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Delete login logo
     */
    @DeleteMapping("/logo/login")
    public ResponseEntity<Map<String, Object>> deleteLoginLogo(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("Admin: Deleting login logo for tenant: {}", tenantId);

        try {
            BrandingDTO current = brandingService.getBrandingByTenantId(tenantId);
            
            if (current != null && current.getLoginLogoUrl() != null) {
                // Delete from S3
                logoService.deleteLogo(tenantId, current.getLoginLogoUrl());

                // Update tenant branding
                BrandingDTO brandingUpdate = BrandingDTO.builder()
                        .loginLogoUrl(null)
                        .build();
                brandingService.updateBranding(tenantId, brandingUpdate, updatedBy);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Login logo deleted successfully");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete login logo for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

