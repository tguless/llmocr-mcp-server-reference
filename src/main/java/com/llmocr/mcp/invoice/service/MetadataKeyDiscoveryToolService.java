package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tools for Metadata Key Discovery
 * 
 * Allows users to discover allowed metadata keys, understand their purpose,
 * and discover categorization rules for metadata storage.
 * Similar to CategoryDiscoveryToolService but for metadata keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataKeyDiscoveryToolService {

    private final MetadataKeyDefinitionRepository keyDefinitionRepository;
    private final McpAuditService mcpAuditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tool: Get all allowed metadata keys with descriptions
     * 
     * Returns comprehensive information about valid metadata keys for the tenant,
     * including descriptions, categories, data types, and examples.
     */
    @Tool(description = "Get all allowed metadata keys for invoices with descriptions, categories, and examples. Helps users understand which metadata keys are valid and what they're used for.")
    @Transactional(readOnly = true)
    public String getAvailableMetadataKeys(
            @ToolParam(description = "Filter by metadata category (optional): GENERATION, RATES, IDENTIFIERS, BILLING, PERFORMANCE, CREDITS, CUSTOM", required = false) String category,
            @ToolParam(description = "Include only required metadata keys (optional)", required = false) Boolean requiredOnly,
            @ToolParam(description = "Include only billable metadata keys (optional)", required = false) Boolean billableOnly) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getAvailableMetadataKeys", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            log.debug("Getting available metadata keys for tenant {}", tenantId);

            List<MetadataKeyDefinition> keyDefinitions;

            // Fetch based on filters
            if (category != null && !category.isEmpty()) {
                keyDefinitions = keyDefinitionRepository.findByTenantIdAndActiveTrueAndCategory(tenantId, category);
            } else {
                keyDefinitions = keyDefinitionRepository.findAllActiveByTenantOrderedByCategory(tenantId);
            }

            // Apply additional filters
            if (Boolean.TRUE.equals(requiredOnly)) {
                keyDefinitions = keyDefinitions.stream()
                        .filter(k -> Boolean.TRUE.equals(k.getRequired()))
                        .collect(Collectors.toList());
            }

            if (Boolean.TRUE.equals(billableOnly)) {
                keyDefinitions = keyDefinitions.stream()
                        .filter(k -> Boolean.TRUE.equals(k.getBillable()))
                        .collect(Collectors.toList());
            }

            List<Map<String, Object>> keys = new ArrayList<>();

            for (MetadataKeyDefinition keyDef : keyDefinitions) {
                Map<String, Object> keyInfo = new HashMap<>();
                keyInfo.put("keyCode", keyDef.getKeyCode());
                keyInfo.put("displayName", keyDef.getDisplayName());
                keyInfo.put("description", keyDef.getDescription());
                keyInfo.put("category", keyDef.getCategory());
                keyInfo.put("dataType", keyDef.getDataType());
                keyInfo.put("exampleValue", keyDef.getExampleValue());
                keyInfo.put("required", keyDef.getRequired());
                keyInfo.put("billable", keyDef.getBillable());

                keys.add(keyInfo);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("metadataKeys", keys);

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalKeys", keys.size());
            summary.put("byCategory", countKeysByCategory(keyDefinitions));
            summary.put("requiredKeys", keyDefinitions.stream().filter(k -> Boolean.TRUE.equals(k.getRequired())).count());
            summary.put("billableKeys", keyDefinitions.stream().filter(k -> Boolean.TRUE.equals(k.getBillable())).count());
            response.put("summary", summary);

            String jsonResponse = formatAsJson(response);
            mcpAuditService.logOperation("TOOL_CALL", "getAvailableMetadataKeys", true,
                    String.format("Retrieved %d metadata keys for tenant", keys.size()), startTime);
            return jsonResponse;

        } catch (Exception e) {
            String errorMessage = String.format("Error retrieving metadata keys: %s", e.getMessage());
            log.error("Error in getAvailableMetadataKeys: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "getAvailableMetadataKeys", false, errorMessage, startTime);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Tool: Validate if a metadata key is allowed
     * 
     * Checks if a given metadata key is registered and active for the tenant.
     * Returns key details if found.
     */
    @Tool(description = "Validate if a metadata key is allowed and retrieve its details. Returns key information if valid, error if not found or inactive.")
    @Transactional(readOnly = true)
    public String validateMetadataKey(
            @ToolParam(description = "The metadata key code to validate (e.g., 'generated_kwh', 'contracted_rate')") String keyCode) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "validateMetadataKey", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            if (keyCode == null || keyCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Metadata key code cannot be empty");
            }

            var keyDefOpt = keyDefinitionRepository.findByTenantIdAndKeyCode(tenantId, keyCode.toLowerCase());

            if (keyDefOpt.isEmpty()) {
                String message = String.format("Metadata key '%s' not found or not allowed for tenant %s", keyCode, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "validateMetadataKey", true, message, startTime);
                Map<String, Object> response = new HashMap<>();
                response.put("valid", false);
                response.put("message", message);
                return formatAsJson(response);
            }

            MetadataKeyDefinition keyDef = keyDefOpt.get();

            if (!Boolean.TRUE.equals(keyDef.getActive())) {
                String message = String.format("Metadata key '%s' is inactive for tenant %s", keyCode, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "validateMetadataKey", true, message, startTime);
                Map<String, Object> response = new HashMap<>();
                response.put("valid", false);
                response.put("message", message);
                return formatAsJson(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("keyCode", keyDef.getKeyCode());
            response.put("displayName", keyDef.getDisplayName());
            response.put("description", keyDef.getDescription());
            response.put("category", keyDef.getCategory());
            response.put("dataType", keyDef.getDataType());
            response.put("required", keyDef.getRequired());
            response.put("billable", keyDef.getBillable());

            mcpAuditService.logOperation("TOOL_CALL", "validateMetadataKey", true,
                    String.format("Validated metadata key: %s", keyCode), startTime);
            return formatAsJson(response);

        } catch (Exception e) {
            String errorMessage = String.format("Error validating metadata key: %s", e.getMessage());
            log.error("Error in validateMetadataKey: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "validateMetadataKey", false, errorMessage, startTime);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Helper method to count keys by category
     */
    private Map<String, Integer> countKeysByCategory(List<MetadataKeyDefinition> keyDefinitions) {
        return keyDefinitions.stream()
                .collect(Collectors.groupingBy(
                        k -> k.getCategory() != null ? k.getCategory() : "OTHER",
                        Collectors.collectingAndThen(Collectors.toList(), List::size)
                ));
    }

    /**
     * Helper method to format response as JSON using ObjectMapper
     */
    private String formatAsJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to serialize response to JSON", e);
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
