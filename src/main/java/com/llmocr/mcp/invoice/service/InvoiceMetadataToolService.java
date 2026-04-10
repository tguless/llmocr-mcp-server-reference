package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.InvoiceMetadata;
import com.llmocr.mcp.invoice.repository.InvoiceMetadataRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Invoice Metadata Tool Service
 * 
 * Provides MCP tools for storing and retrieving customer-specific key-value pairs
 * associated with invoices. This allows for flexible metadata storage without
 * modifying the core invoice schema.
 * 
 * CRITICAL: All metadata keys are validated against the allowed metadata key definitions
 * to ensure only approved keys are stored.
 * 
 * Use cases:
 * - Generated kWh values
 * - Contracted rates
 * - Custom calculations
 * - Industry-specific data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceMetadataToolService {

    private final InvoiceMetadataRepository metadataRepository;
    private final InvoiceRepository invoiceRepository;
    private final MetadataKeyDefinitionRepository metadataKeyRepository;
    private final McpAuditService mcpAuditService;
    private final MetadataValidationService validationService;

    /**
     * Store a key-value pair for an invoice
     * 
     * Creates or updates metadata for the given invoice and key.
     * VALIDATES that the key is in the allowed metadata key definitions.
     * Returns meaningful error if key is not allowed.
     */
    @Tool(description = "Store a key-value pair for an invoice. Key must be an approved metadata key. Returns success/error message with validation details.")
    @Transactional
    public String storeInvoiceMetadata(
            @ToolParam(description = "The ID of the invoice to store metadata for.") Long invoiceId,
            @ToolParam(description = "The metadata key (e.g., 'generated_kwh', 'contracted_rate'). Must be an allowed key.") String key,
            @ToolParam(description = "The value for the metadata entry.") String value) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            // Validate inputs
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("Metadata key cannot be empty");
            }
            if (value == null) {
                throw new IllegalArgumentException("Metadata value cannot be null");
            }

            // Verify invoice exists in tenant
            if (!invoiceRepository.existsByTenantIdAndId(tenantId, invoiceId)) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // VALIDATE: Check if this metadata key is allowed
            String normalizedKey = key.toLowerCase().trim();
            var keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, normalizedKey);
            
            if (keyDefOpt.isEmpty()) {
                String message = String.format(
                    "VALIDATION ERROR: Metadata key '%s' is not an approved key for tenant %s. " +
                    "Use getAvailableMetadataKeys() to discover allowed keys. " +
                    "This key is not in the metadata key registry.",
                    key, tenantId);
                log.warn("Rejected invalid metadata key: {} for tenant {}", key, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            var keyDef = keyDefOpt.get();
            if (!Boolean.TRUE.equals(keyDef.getActive())) {
                String message = String.format(
                    "VALIDATION ERROR: Metadata key '%s' is currently INACTIVE for tenant %s. " +
                    "This key has been disabled. Use getAvailableMetadataKeys() to find active alternatives.",
                    key, tenantId);
                log.warn("Rejected inactive metadata key: {} for tenant {}", key, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // VALIDATE: Check if value matches expected data type
            MetadataValidationService.ValidationResult validationResult = validationService.validate(keyDef, value);
            if (!validationResult.isValid()) {
                String message = String.format(
                    "VALIDATION ERROR: %s Expected data type: %s. Example: %s",
                    validationResult.getErrorMessage(),
                    keyDef.getDataType(),
                    keyDef.getExampleValue());
                log.warn("Rejected invalid metadata value for key '{}': {}", key, validationResult.getErrorMessage());
                mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // Store or update metadata
            var existingOpt = metadataRepository.findByTenantIdAndInvoiceIdAndKey(tenantId, invoiceId, normalizedKey);
            InvoiceMetadata metadata;

            if (existingOpt.isPresent()) {
                metadata = existingOpt.get();
                metadata.setValue(value);
                metadata.setUpdatedBy(userId);
                log.info("Updating metadata key='{}' for invoice {} in tenant {}", normalizedKey, invoiceId, tenantId);
            } else {
                metadata = InvoiceMetadata.builder()
                        .tenantId(tenantId)
                        .invoiceId(invoiceId)
                        .key(normalizedKey)
                        .value(value)
                        .createdBy(userId)
                        .updatedBy(userId)
                        .build();
                log.info("Creating metadata key='{}' for invoice {} in tenant {}", normalizedKey, invoiceId, tenantId);
            }

            metadataRepository.save(metadata);
            String successMessage = String.format(
                "SUCCESS: Metadata stored - key=%s (%s), invoice=%d, value=%s", 
                normalizedKey, keyDef.getDisplayName(), invoiceId, value);
            mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", true, successMessage, startTime);
            return successMessage;

        } catch (Exception e) {
            String errorMessage = String.format("ERROR: Error storing metadata: %s", e.getMessage());
            log.error("Error in storeInvoiceMetadata: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "storeInvoiceMetadata", false, errorMessage, startTime);
            return errorMessage;
        }
    }

    /**
     * Retrieve a specific metadata value for an invoice
     * 
     * Returns the value associated with the given key for the invoice.
     */
    @Tool(description = "Retrieve a specific metadata value for an invoice. Returns the value or null if not found.")
    @Transactional(readOnly = true)
    public String getInvoiceMetadata(
            @ToolParam(description = "The ID of the invoice.") Long invoiceId,
            @ToolParam(description = "The metadata key to retrieve.") String key) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceMetadata", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("Metadata key cannot be empty");
            }

            // Verify invoice exists
            if (!invoiceRepository.existsByTenantIdAndId(tenantId, invoiceId)) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "getInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            var metadataOpt = metadataRepository.findByTenantIdAndInvoiceIdAndKey(tenantId, invoiceId, key.toLowerCase());
            
            if (metadataOpt.isPresent()) {
                String value = metadataOpt.get().getValue();
                mcpAuditService.logOperation("TOOL_CALL", "getInvoiceMetadata", true, 
                        String.format("Retrieved metadata - invoice=%d, key=%s", invoiceId, key), startTime);
                return value;
            } else {
                String message = String.format("Metadata key '%s' not found for invoice %d", key, invoiceId);
                mcpAuditService.logOperation("TOOL_CALL", "getInvoiceMetadata", true, message, startTime);
                return null;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error retrieving metadata: %s", e.getMessage());
            log.error("Error in getInvoiceMetadata: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceMetadata", false, errorMessage, startTime);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Retrieve all metadata for an invoice
     * 
     * Returns a JSON map of all key-value pairs for the invoice.
     */
    @Tool(description = "Retrieve all metadata key-value pairs for an invoice. Returns JSON-formatted key-value pairs.")
    @Transactional(readOnly = true)
    public String getAllInvoiceMetadata(
            @ToolParam(description = "The ID of the invoice.") Long invoiceId) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getAllInvoiceMetadata", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }

            // Verify invoice exists
            if (!invoiceRepository.existsByTenantIdAndId(tenantId, invoiceId)) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "getAllInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            List<InvoiceMetadata> metadataList = metadataRepository.findByTenantIdAndInvoiceId(tenantId, invoiceId);
            
            if (metadataList.isEmpty()) {
                mcpAuditService.logOperation("TOOL_CALL", "getAllInvoiceMetadata", true,
                        String.format("No metadata found for invoice=%d", invoiceId), startTime);
                return "{}";
            }

            // Convert to JSON map
            Map<String, String> metadataMap = metadataList.stream()
                    .collect(Collectors.toMap(InvoiceMetadata::getKey, InvoiceMetadata::getValue));
            
            String jsonResult = formatMetadataAsJson(metadataMap);
            mcpAuditService.logOperation("TOOL_CALL", "getAllInvoiceMetadata", true,
                    String.format("Retrieved %d metadata entries for invoice=%d", metadataList.size(), invoiceId), startTime);
            return jsonResult;

        } catch (Exception e) {
            String errorMessage = String.format("Error retrieving all metadata: %s", e.getMessage());
            log.error("Error in getAllInvoiceMetadata: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "getAllInvoiceMetadata", false, errorMessage, startTime);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Delete a specific metadata entry
     * 
     * Removes the metadata key-value pair for the given invoice.
     */
    @Tool(description = "Delete a specific metadata key-value pair for an invoice. Returns success/error message.")
    @Transactional
    public String deleteInvoiceMetadata(
            @ToolParam(description = "The ID of the invoice.") Long invoiceId,
            @ToolParam(description = "The metadata key to delete.") String key) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "deleteInvoiceMetadata", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("Metadata key cannot be empty");
            }

            // Verify invoice exists
            if (!invoiceRepository.existsByTenantIdAndId(tenantId, invoiceId)) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "deleteInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // Check if metadata exists
            if (!metadataRepository.existsByTenantIdAndInvoiceIdAndKey(tenantId, invoiceId, key)) {
                String message = String.format("Metadata key '%s' not found for invoice %d", key, invoiceId);
                mcpAuditService.logOperation("TOOL_CALL", "deleteInvoiceMetadata", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            metadataRepository.deleteByTenantIdAndInvoiceIdAndKey(tenantId, invoiceId, key);
            String successMessage = String.format("Metadata deleted successfully - invoice=%d, key=%s", invoiceId, key);
            log.info("Deleted metadata key='{}' for invoice {} in tenant {}", key, invoiceId, tenantId);
            mcpAuditService.logOperation("TOOL_CALL", "deleteInvoiceMetadata", true, successMessage, startTime);
            return "SUCCESS: " + successMessage;

        } catch (Exception e) {
            String errorMessage = String.format("Error deleting metadata: %s", e.getMessage());
            log.error("Error in deleteInvoiceMetadata: ", e);
            mcpAuditService.logOperation("TOOL_CALL", "deleteInvoiceMetadata", false, errorMessage, startTime);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Helper method to format metadata as JSON string
     */
    private String formatMetadataAsJson(Map<String, String> metadataMap) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                .append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Helper method to escape JSON special characters
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
