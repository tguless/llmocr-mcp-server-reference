package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Controller for Metadata Key Management (Admin UI)
 * 
 * Similar to CategoryApiController, this provides REST endpoints for the admin UI
 * to discover and manage allowed metadata keys for invoices.
 */
@RestController
@RequestMapping("/api/metadata-keys")
@RequiredArgsConstructor
@Slf4j
public class MetadataKeyApiController {

    private final MetadataKeyDefinitionRepository metadataKeyRepository;

    /**
     * Get all available metadata keys with their descriptions and categories
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMetadataKeys(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        log.debug("REST API: Getting metadata keys for tenant: {} (category={}, active={})", tenantId, category, active);

        List<MetadataKeyDefinition> keys;
        
        if (category != null && !category.isEmpty()) {
            keys = metadataKeyRepository.findByTenantIdAndActiveTrueAndCategory(tenantId, category);
        } else {
            keys = metadataKeyRepository.findByTenantIdOrderByDisplayName(tenantId);
        }
        
        // Filter by active status if requested
        if (active != null) {
            keys = keys.stream()
                    .filter(k -> active.equals(k.getActive()))
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> response = keys.stream()
                .map(this::toKeyInfo)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific metadata key definition
     */
    @GetMapping("/{keyCode}")
    public ResponseEntity<Map<String, Object>> getMetadataKey(
            @PathVariable String keyCode,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        log.debug("REST API: Getting metadata key {} for tenant: {}", keyCode, tenantId);

        var keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, keyCode.toLowerCase());
        
        if (keyDefOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toKeyInfo(keyDefOpt.get()));
    }

    /**
     * Get available categories with counts
     */
    @GetMapping("/categories/summary")
    public ResponseEntity<Map<String, Object>> getCategoriesSummary(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        log.debug("REST API: Getting metadata key categories summary for tenant: {}", tenantId);

        List<MetadataKeyDefinition> keys = metadataKeyRepository.findByTenantIdOrderByDisplayName(tenantId);
        
        Map<String, Object> summary = new LinkedHashMap<>();
        
        // Count by category
        Map<String, Long> categoryCount = keys.stream()
                .filter(k -> Boolean.TRUE.equals(k.getActive()))
                .collect(Collectors.groupingBy(
                        k -> k.getCategory() != null ? k.getCategory() : "OTHER",
                        Collectors.counting()
                ));
        summary.put("byCategory", categoryCount);
        
        // Overall counts
        long totalKeys = keys.size();
        long activeKeys = keys.stream().filter(k -> Boolean.TRUE.equals(k.getActive())).count();
        long requiredKeys = keys.stream().filter(k -> Boolean.TRUE.equals(k.getRequired())).count();
        long billableKeys = keys.stream().filter(k -> Boolean.TRUE.equals(k.getBillable())).count();
        
        summary.put("totalKeys", totalKeys);
        summary.put("activeKeys", activeKeys);
        summary.put("requiredKeys", requiredKeys);
        summary.put("billableKeys", billableKeys);
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Create a new metadata key definition
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMetadataKey(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        
        String tenantId = (String) httpRequest.getAttribute("tenantId");
        String userId = (String) httpRequest.getAttribute("userId");
        
        String keyCode = ((String) request.get("keyCode")).toLowerCase();
        String displayName = (String) request.get("displayName");
        String description = (String) request.get("description");
        String category = (String) request.getOrDefault("category", "CUSTOM");
        
        log.info("REST API: Creating metadata key {} for tenant: {}", keyCode, tenantId);

        if (metadataKeyRepository.existsByTenantIdAndKeyCode(tenantId, keyCode)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("message", "Metadata key already exists: " + keyCode);
            return ResponseEntity.badRequest().body(error);
        }

        MetadataKeyDefinition keyDef = MetadataKeyDefinition.builder()
                .tenantId(tenantId)
                .keyCode(keyCode)
                .displayName(displayName)
                .description(description)
                .category(category)
                .dataType((String) request.getOrDefault("dataType", "STRING"))
                .exampleValue((String) request.get("exampleValue"))
                .required((Boolean) request.getOrDefault("required", false))
                .billable((Boolean) request.getOrDefault("billable", false))
                .active(true)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        MetadataKeyDefinition saved = metadataKeyRepository.save(keyDef);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Metadata key created successfully");
        response.put("data", toKeyInfo(saved));

        return ResponseEntity.ok(response);
    }

    /**
     * Update a metadata key definition
     */
    @PutMapping("/{keyCode}")
    public ResponseEntity<Map<String, Object>> updateMetadataKey(
            @PathVariable String keyCode,
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String userId = (String) request.getAttribute("userId");
        
        log.info("REST API: Updating metadata key {} for tenant: {}", keyCode, tenantId);

        var keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, keyCode.toLowerCase());
        
        if (keyDefOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MetadataKeyDefinition keyDef = keyDefOpt.get();
        
        if (updates.containsKey("displayName")) {
            keyDef.setDisplayName((String) updates.get("displayName"));
        }
        if (updates.containsKey("description")) {
            keyDef.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("category")) {
            keyDef.setCategory((String) updates.get("category"));
        }
        if (updates.containsKey("dataType")) {
            keyDef.setDataType((String) updates.get("dataType"));
        }
        if (updates.containsKey("exampleValue")) {
            keyDef.setExampleValue((String) updates.get("exampleValue"));
        }
        if (updates.containsKey("required")) {
            keyDef.setRequired((Boolean) updates.get("required"));
        }
        if (updates.containsKey("billable")) {
            keyDef.setBillable((Boolean) updates.get("billable"));
        }
        if (updates.containsKey("active")) {
            keyDef.setActive((Boolean) updates.get("active"));
        }
        
        keyDef.setUpdatedBy(userId);
        MetadataKeyDefinition updated = metadataKeyRepository.save(keyDef);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Metadata key updated successfully");
        response.put("data", toKeyInfo(updated));

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a metadata key definition
     */
    @DeleteMapping("/{keyCode}")
    public ResponseEntity<Map<String, String>> deleteMetadataKey(
            @PathVariable String keyCode,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        log.info("REST API: Deleting metadata key {} for tenant: {}", keyCode, tenantId);

        var keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, keyCode.toLowerCase());
        
        if (keyDefOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        metadataKeyRepository.delete(keyDefOpt.get());
        
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Metadata key deleted successfully");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Convert a MetadataKeyDefinition entity to a map for API response
     */
    private Map<String, Object> toKeyInfo(MetadataKeyDefinition keyDef) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("keyCode", keyDef.getKeyCode());
        info.put("displayName", keyDef.getDisplayName());
        info.put("description", keyDef.getDescription());
        info.put("category", keyDef.getCategory());
        info.put("dataType", keyDef.getDataType());
        info.put("exampleValue", keyDef.getExampleValue());
        info.put("required", keyDef.getRequired());
        info.put("billable", keyDef.getBillable());
        info.put("active", keyDef.getActive());
        info.put("createdAt", keyDef.getCreatedAt());
        info.put("updatedAt", keyDef.getUpdatedAt());
        return info;
    }
}
