package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Tenant;
import com.llmocr.mcp.invoice.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Tenant Validation Settings
 */
@RestController
@RequestMapping("/api/admin/tenant/validation-settings")
@RequiredArgsConstructor
@Slf4j
public class TenantValidationSettingsController {

    private final TenantRepository tenantRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getValidationSettings(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        log.info("REST API: Retrieving validation settings for tenant: {}", tenantId);

        try {
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantId(tenantId);
            if (tenantOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "message", "Tenant not found"
                ));
            }

            Tenant tenant = tenantOpt.get();
            Map<String, Object> settings = Map.of(
                    "servicePeriodRequired", tenant.getServicePeriodRequired() != null ? tenant.getServicePeriodRequired() : true
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", settings
            ));

        } catch (Exception e) {
            log.error("Error retrieving validation settings for tenant {}: {}", tenantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to retrieve validation settings: " + e.getMessage()
            ));
        }
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateValidationSettings(
            @RequestBody Map<String, Object> settingsUpdate,
            HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        log.info("REST API: Updating validation settings for tenant: {}", tenantId);

        try {
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantId(tenantId);
            if (tenantOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "message", "Tenant not found"
                ));
            }

            Tenant tenant = tenantOpt.get();

            // Update service period required flag if provided
            if (settingsUpdate.containsKey("servicePeriodRequired")) {
                Boolean servicePeriodRequired = (Boolean) settingsUpdate.get("servicePeriodRequired");
                tenant.setServicePeriodRequired(servicePeriodRequired);
            }

            tenant.setUpdatedBy(updatedBy);
            tenantRepository.save(tenant);

            log.info("✅ Validation settings updated successfully for tenant '{}'", tenantId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Validation settings updated successfully",
                    "data", Map.of(
                            "servicePeriodRequired", tenant.getServicePeriodRequired()
                    )
            ));

        } catch (Exception e) {
            log.error("Error updating validation settings for tenant {}: {}", tenantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update validation settings: " + e.getMessage()
            ));
        }
    }
}

