package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller for hydrating tenants with data from other tenants
 * GLOBAL_ADMIN ONLY - Allows copying categories and metadata key definitions between ANY tenants
 */
@RestController
@RequestMapping("/api/admin/tenant-hydration")
@RequiredArgsConstructor
@Slf4j
public class TenantHydrationController {

    private final CategoryRepository categoryRepository;
    private final MetadataKeyDefinitionRepository metadataKeyRepository;

    /**
     * Check if user is GLOBAL_ADMIN
     */
    private ResponseEntity<?> checkGlobalAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "error", "Authentication required"
            ));
        }

        if (currentUser.getRole() != User.UserRole.GLOBAL_ADMIN) {
            log.warn("Unauthorized tenant hydration attempt by user {} with role {}", 
                    currentUser.getUsername(), currentUser.getRole());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "error", "Access denied. This feature is only available to GLOBAL_ADMIN users."
            ));
        }

        return null; // Null means authorized
    }

    /**
     * Get list of available tenants (those with categories or metadata keys)
     * GLOBAL_ADMIN ONLY
     */
    @GetMapping("/available-tenants")
    public ResponseEntity<?> getAvailableTenants(HttpServletRequest httpRequest) {
        // Check authorization
        ResponseEntity<?> authCheck = checkGlobalAdmin(httpRequest);
        if (authCheck != null) return authCheck;

        try {
            User currentUser = (User) httpRequest.getAttribute("user");
            log.info("GLOBAL_ADMIN {} getting available tenants for hydration", currentUser.getUsername());

            // Get unique tenant IDs from categories
            List<String> categoryTenants = categoryRepository.findDistinctTenantIds();
            
            // Get unique tenant IDs from metadata keys
            List<String> metadataTenants = metadataKeyRepository.findDistinctTenantIds();

            // Combine and deduplicate
            Set<String> allTenants = new HashSet<>();
            allTenants.addAll(categoryTenants);
            allTenants.addAll(metadataTenants);

            // Create response with tenant info
            List<Map<String, Object>> tenantInfo = new ArrayList<>();
            for (String tenantId : allTenants) {
                long categoryCount = categoryRepository.countByTenantIdAndIsActiveTrue(tenantId);
                long metadataCount = metadataKeyRepository.countByTenantId(tenantId);
                
                tenantInfo.add(Map.of(
                    "tenantId", tenantId,
                    "categoryCount", categoryCount,
                    "metadataKeyCount", metadataCount
                ));
            }

            // Sort by tenant ID
            tenantInfo.sort(Comparator.comparing(t -> (String) t.get("tenantId")));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "tenants", tenantInfo
            ));

        } catch (Exception e) {
            log.error("Error getting available tenants", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to get available tenants: " + e.getMessage()
            ));
        }
    }

    /**
     * Preview what would be copied from source to target tenant
     * GLOBAL_ADMIN ONLY
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewHydration(
            @RequestParam String sourceTenantId,
            @RequestParam String targetTenantId,
            HttpServletRequest httpRequest) {
        // Check authorization
        ResponseEntity<?> authCheck = checkGlobalAdmin(httpRequest);
        if (authCheck != null) return authCheck;

        try {
            User currentUser = (User) httpRequest.getAttribute("user");
            log.info("GLOBAL_ADMIN {} previewing hydration from {} to {}", 
                    currentUser.getUsername(), sourceTenantId, targetTenantId);

            // Get source categories
            List<Category> sourceCategories = categoryRepository.findByTenantIdAndIsActiveTrue(sourceTenantId);
            
            // Get target categories
            List<Category> targetCategories = categoryRepository.findByTenantIdAndIsActiveTrue(targetTenantId);
            Set<String> targetCategoryCodes = new HashSet<>();
            for (Category cat : targetCategories) {
                targetCategoryCodes.add(cat.getCategoryCode());
            }

            // Determine which categories can be copied
            List<Map<String, String>> copyableCategories = new ArrayList<>();
            List<Map<String, String>> conflictingCategories = new ArrayList<>();
            
            for (Category cat : sourceCategories) {
                Map<String, String> info = Map.of(
                    "categoryCode", cat.getCategoryCode(),
                    "categoryName", cat.getCategoryName(),
                    "description", cat.getDescription() != null ? cat.getDescription() : ""
                );
                
                if (targetCategoryCodes.contains(cat.getCategoryCode())) {
                    conflictingCategories.add(info);
                } else {
                    copyableCategories.add(info);
                }
            }

            // Get source metadata keys
            List<MetadataKeyDefinition> sourceKeys = metadataKeyRepository.findByTenantId(sourceTenantId);
            
            // Get target metadata keys
            List<MetadataKeyDefinition> targetKeys = metadataKeyRepository.findByTenantId(targetTenantId);
            Set<String> targetKeyCodes = new HashSet<>();
            for (MetadataKeyDefinition key : targetKeys) {
                targetKeyCodes.add(key.getKeyCode());
            }

            // Determine which metadata keys can be copied
            List<Map<String, String>> copyableMetadataKeys = new ArrayList<>();
            List<Map<String, String>> conflictingMetadataKeys = new ArrayList<>();
            
            for (MetadataKeyDefinition key : sourceKeys) {
                Map<String, String> info = Map.of(
                    "keyCode", key.getKeyCode(),
                    "displayName", key.getDisplayName(),
                    "description", key.getDescription() != null ? key.getDescription() : ""
                );
                
                if (targetKeyCodes.contains(key.getKeyCode())) {
                    conflictingMetadataKeys.add(info);
                } else {
                    copyableMetadataKeys.add(info);
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "preview", Map.of(
                    "sourceTenantId", sourceTenantId,
                    "targetTenantId", targetTenantId,
                    "categories", Map.of(
                        "copyable", copyableCategories,
                        "conflicts", conflictingCategories
                    ),
                    "metadataKeys", Map.of(
                        "copyable", copyableMetadataKeys,
                        "conflicts", conflictingMetadataKeys
                    )
                )
            ));

        } catch (Exception e) {
            log.error("Error previewing hydration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to preview hydration: " + e.getMessage()
            ));
        }
    }

    /**
     * Hydrate target tenant with data from source tenant
     * GLOBAL_ADMIN ONLY
     */
    @PostMapping("/hydrate")
    public ResponseEntity<?> hydrateTenant(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        // Check authorization
        ResponseEntity<?> authCheck = checkGlobalAdmin(httpRequest);
        if (authCheck != null) return authCheck;

        try {
            User currentUser = (User) httpRequest.getAttribute("user");
            String sourceTenantId = (String) request.get("sourceTenantId");
            String targetTenantId = (String) request.get("targetTenantId");
            Boolean copyCategories = (Boolean) request.getOrDefault("copyCategories", true);
            Boolean copyMetadataKeys = (Boolean) request.getOrDefault("copyMetadataKeys", true);

            log.info("GLOBAL_ADMIN {} hydrating tenant {} from {}. Categories: {}, MetadataKeys: {}", 
                    currentUser.getUsername(), targetTenantId, sourceTenantId, copyCategories, copyMetadataKeys);

            if (sourceTenantId == null || sourceTenantId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Source tenant ID is required"
                ));
            }

            if (targetTenantId == null || targetTenantId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Target tenant ID is required"
                ));
            }

            if (sourceTenantId.equals(targetTenantId)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Source and target tenant cannot be the same"
                ));
            }

            List<String> copiedCategories = new ArrayList<>();
            List<String> skippedCategories = new ArrayList<>();
            List<String> copiedMetadataKeys = new ArrayList<>();
            List<String> skippedMetadataKeys = new ArrayList<>();

            // Copy categories
            if (copyCategories) {
                List<Category> sourceCategories = categoryRepository.findByTenantIdAndIsActiveTrue(sourceTenantId);
                Set<String> targetCategoryCodes = new HashSet<>();
                
                List<Category> targetCategories = categoryRepository.findByTenantIdAndIsActiveTrue(targetTenantId);
                for (Category cat : targetCategories) {
                    targetCategoryCodes.add(cat.getCategoryCode());
                }

                for (Category sourceCategory : sourceCategories) {
                    if (targetCategoryCodes.contains(sourceCategory.getCategoryCode())) {
                        skippedCategories.add(sourceCategory.getCategoryCode() + " (already exists)");
                        log.debug("Skipping category {} - already exists in target tenant", sourceCategory.getCategoryCode());
                    } else {
                        // Create new category for target tenant
                        Category newCategory = Category.builder()
                                .tenantId(targetTenantId)
                                .categoryCode(sourceCategory.getCategoryCode())
                                .categoryName(sourceCategory.getCategoryName())
                                .description(sourceCategory.getDescription())
                                .keywords(sourceCategory.getKeywords())
                                .examples(sourceCategory.getExamples())
                                .categorizationInstructions(sourceCategory.getCategorizationInstructions())
                                .isPassThrough(sourceCategory.getIsPassThrough())
                                .isCredit(sourceCategory.getIsCredit())
                                .isBillable(sourceCategory.getIsBillable())
                                .isActive(true)
                                .displayOrder(sourceCategory.getDisplayOrder())
                                .createdBy(currentUser.getUsername())
                                .updatedBy(currentUser.getUsername())
                                .build();
                        
                        categoryRepository.save(newCategory);
                        copiedCategories.add(sourceCategory.getCategoryCode());
                        log.info("Copied category {} from {} to {}", sourceCategory.getCategoryCode(), sourceTenantId, targetTenantId);
                    }
                }
            }

            // Copy metadata keys
            if (copyMetadataKeys) {
                List<MetadataKeyDefinition> sourceKeys = metadataKeyRepository.findByTenantId(sourceTenantId);
                Set<String> targetKeyCodes = new HashSet<>();
                
                List<MetadataKeyDefinition> targetKeys = metadataKeyRepository.findByTenantId(targetTenantId);
                for (MetadataKeyDefinition key : targetKeys) {
                    targetKeyCodes.add(key.getKeyCode());
                }

                for (MetadataKeyDefinition sourceKey : sourceKeys) {
                    if (targetKeyCodes.contains(sourceKey.getKeyCode())) {
                        skippedMetadataKeys.add(sourceKey.getKeyCode() + " (already exists)");
                        log.debug("Skipping metadata key {} - already exists in target tenant", sourceKey.getKeyCode());
                    } else {
                        // Create new metadata key for target tenant
                        MetadataKeyDefinition newKey = MetadataKeyDefinition.builder()
                                .tenantId(targetTenantId)
                                .keyCode(sourceKey.getKeyCode())
                                .displayName(sourceKey.getDisplayName())
                                .description(sourceKey.getDescription())
                                .category(sourceKey.getCategory())
                                .dataType(sourceKey.getDataType())
                                .exampleValue(sourceKey.getExampleValue())
                                .required(sourceKey.getRequired())
                                .billable(sourceKey.getBillable())
                                .active(sourceKey.getActive())
                                .createdBy(currentUser.getUsername())
                                .updatedBy(currentUser.getUsername())
                                .build();
                        
                        metadataKeyRepository.save(newKey);
                        copiedMetadataKeys.add(sourceKey.getKeyCode());
                        log.info("Copied metadata key {} from {} to {}", sourceKey.getKeyCode(), sourceTenantId, targetTenantId);
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", Map.of(
                    "sourceTenantId", sourceTenantId,
                    "targetTenantId", targetTenantId,
                    "categories", Map.of(
                        "copied", copiedCategories,
                        "skipped", skippedCategories,
                        "copiedCount", copiedCategories.size(),
                        "skippedCount", skippedCategories.size()
                    ),
                    "metadataKeys", Map.of(
                        "copied", copiedMetadataKeys,
                        "skipped", skippedMetadataKeys,
                        "copiedCount", copiedMetadataKeys.size(),
                        "skippedCount", skippedMetadataKeys.size()
                    )
                ),
                "message", String.format("Hydration complete: %d categories and %d metadata keys copied", 
                        copiedCategories.size(), copiedMetadataKeys.size())
            ));

        } catch (Exception e) {
            log.error("Error hydrating tenant", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to hydrate tenant: " + e.getMessage()
            ));
        }
    }
}

