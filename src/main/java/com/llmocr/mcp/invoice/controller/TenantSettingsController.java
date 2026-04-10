package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.TenantSettings;
import com.llmocr.mcp.invoice.repository.TenantSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API Controller for Tenant Settings Management.
 * 
 * Allows users to configure tenant-level settings such as LLMOCR API key.
 */
@RestController
@RequestMapping("/api/admin/tenant-settings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class TenantSettingsController {

    private final TenantSettingsRepository tenantSettingsRepository;

    /**
     * Get tenant settings (creates default if not exists).
     */
    @GetMapping
    public ResponseEntity<?> getTenantSettings(HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            log.info("Fetching tenant settings for tenant: {}", tenantId);

            TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                    .orElseGet(() -> {
                        log.info("Creating default tenant settings for tenant: {}", tenantId);
                        return tenantSettingsRepository.save(TenantSettings.builder()
                                .tenantId(tenantId)
                                .isActive(true)
                                .build());
                    });

            // Return safe response (no raw API key)
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", settings.getId(),
                            "tenantId", settings.getTenantId(),
                            "hasLlmOcrApiKey", settings.getLlmOcrApiKey() != null && !settings.getLlmOcrApiKey().isEmpty(),
                            "llmOcrApiKeyMasked", settings.getLlmOcrApiKeyMasked() != null ? settings.getLlmOcrApiKeyMasked() : "",
                            "isActive", settings.getIsActive(),
                            "createdAt", settings.getCreatedAt(),
                            "updatedAt", settings.getUpdatedAt()
                    )
            ));
        } catch (Exception e) {
            log.error("Failed to fetch tenant settings: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve tenant settings: " + e.getMessage()
            ));
        }
    }

    /**
     * Update LLMOCR API key for tenant.
     */
    @PutMapping("/llmocr-api-key")
    public ResponseEntity<?> updateLlmOcrApiKey(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            String userId = (String) request.getAttribute("userId");
            String apiKey = requestBody.get("apiKey");

            log.info("Updating LLMOCR API key for tenant: {}", tenantId);

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "API key is required"
                ));
            }

            // Validate API key format
            if (!apiKey.startsWith("llmocr_svc_")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid API key format. Expected format: llmocr_svc_..."
                ));
            }

            TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                    .orElseGet(() -> TenantSettings.builder()
                            .tenantId(tenantId)
                            .createdBy(userId)
                            .isActive(true)
                            .build());

            settings.setLlmOcrApiKey(apiKey);
            settings.setLlmOcrApiKeyMasked(TenantSettings.maskApiKey(apiKey));
            settings.setUpdatedBy(userId);

            tenantSettingsRepository.save(settings);

            log.info("Successfully updated LLMOCR API key for tenant: {}", tenantId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "LLMOCR API key updated successfully",
                    "data", Map.of(
                            "hasLlmOcrApiKey", true,
                            "llmOcrApiKeyMasked", settings.getLlmOcrApiKeyMasked()
                    )
            ));
        } catch (Exception e) {
            log.error("Failed to update LLMOCR API key: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to update API key: " + e.getMessage()
            ));
        }
    }

    /**
     * Remove LLMOCR API key for tenant.
     */
    @DeleteMapping("/llmocr-api-key")
    public ResponseEntity<?> removeLlmOcrApiKey(HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            String userId = (String) request.getAttribute("userId");

            log.info("Removing LLMOCR API key for tenant: {}", tenantId);

            TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                    .orElse(null);

            if (settings != null) {
                settings.setLlmOcrApiKey(null);
                settings.setLlmOcrApiKeyMasked(null);
                settings.setUpdatedBy(userId);
                tenantSettingsRepository.save(settings);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "LLMOCR API key removed successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to remove LLMOCR API key: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to remove API key: " + e.getMessage()
            ));
        }
    }
}

