package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.InvoiceMetadata;
import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import com.llmocr.mcp.invoice.domain.Tenant;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceMetadataRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import com.llmocr.mcp.invoice.repository.TenantRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tool for validating invoice completeness
 * 
 * This is the FINAL validation step that should be called after all invoice data has been populated.
 * It ensures all required fields are present before marking the invoice as complete.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceValidationToolService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMetadataRepository metadataRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final CategoryRepository categoryRepository;
    private final MetadataKeyDefinitionRepository metadataKeyRepository;
    private final TenantRepository tenantRepository;
    private final McpAuditService mcpAuditService;

    @Tool(
        name = "validateInvoice",
        description = "**FINAL VALIDATION STEP** - Call this tool LAST after all invoice data has been populated. " +
                "Validates that all required fields are present: service period (if required), required metadata keys, " +
                "line item categories, and required category coverage. " +
                "Returns a detailed validation report with any missing or incomplete data. " +
                "This is your final checklist before completing invoice processing."
    )
    public String validateInvoice(
            @ToolParam(description = "The ID of the invoice to validate") Long invoiceId) {
        
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "validateInvoice", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            // Validate inputs
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }

            // Verify invoice exists in tenant
            Optional<Invoice> invoiceOpt = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId);
            if (invoiceOpt.isEmpty()) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "validateInvoice", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            Invoice invoice = invoiceOpt.get();
            
            // Perform validation
            ValidationResult result = performValidation(invoice, tenantId);
            
            // Log success
            mcpAuditService.logOperation("TOOL_CALL", "validateInvoice", true, null, startTime);
            
            // Return formatted result
            return formatValidationResult(result);

        } catch (Exception e) {
            log.error("Failed to validate invoice {}: {}", invoiceId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "validateInvoice", false, e.getMessage(), startTime);
            throw new RuntimeException("Failed to validate invoice: " + e.getMessage(), e);
        }
    }

    private ValidationResult performValidation(Invoice invoice, String tenantId) {
        ValidationResult result = new ValidationResult();
        result.invoiceId = invoice.getId();
        result.invoiceNumber = invoice.getInvoiceNumber();
        
        // Get tenant configuration
        Optional<Tenant> tenantOpt = tenantRepository.findByTenantId(tenantId);
        boolean servicePeriodRequired = tenantOpt.map(t -> 
                t.getServicePeriodRequired() != null ? t.getServicePeriodRequired() : true
        ).orElse(true);
        
        // 1. Check service period (if required by tenant configuration)
        if (servicePeriodRequired && 
            (invoice.getServicePeriodStartDate() == null || invoice.getServicePeriodEndDate() == null)) {
            result.missingServicePeriod = true;
            result.errors.add("Service period (start and end dates) is required but not set");
        }

        // 2. Check required metadata keys
        List<MetadataKeyDefinition> requiredKeys = metadataKeyRepository
                .findByTenantIdAndActiveTrueAndRequiredTrue(tenantId);
        
        List<InvoiceMetadata> existingMetadata = metadataRepository
                .findByTenantIdAndInvoiceId(tenantId, invoice.getId());
        
        Set<String> existingMetadataKeys = existingMetadata.stream()
                .map(InvoiceMetadata::getKey)
                .collect(Collectors.toSet());

        for (MetadataKeyDefinition requiredKey : requiredKeys) {
            if (!existingMetadataKeys.contains(requiredKey.getKeyCode())) {
                result.missingMetadataKeys.add(String.format("%s (%s)", 
                        requiredKey.getKeyCode(), requiredKey.getDisplayName()));
                result.errors.add(String.format("Required metadata key '%s' is missing", 
                        requiredKey.getKeyCode()));
            }
        }

        // 3. Check all line items have categories assigned
        List<InvoiceLineItem> lineItems = lineItemRepository
                .findByInvoiceIdAndTenantIdOrderByLineNumber(invoice.getId(), tenantId);
        
        for (InvoiceLineItem lineItem : lineItems) {
            if (lineItem.getCategory() == null || lineItem.getCategory().trim().isEmpty()) {
                result.uncategorizedLineItems.add(String.format("Line %d: %s (Amount: %.2f)", 
                        lineItem.getLineNumber(), lineItem.getDescription(), lineItem.getLineTotal()));
                result.errors.add(String.format("Line item %d is not categorized", 
                        lineItem.getLineNumber()));
            }
        }

        // 4. Check required categories have at least one line item
        List<Category> requiredCategories = categoryRepository
                .findByTenantIdAndIsActiveTrue(tenantId).stream()
                .filter(c -> c.getRequired() != null && c.getRequired())
                .collect(Collectors.toList());

        Map<String, Long> categoryCounts = lineItems.stream()
                .filter(li -> li.getCategory() != null && !li.getCategory().trim().isEmpty())
                .collect(Collectors.groupingBy(InvoiceLineItem::getCategory, Collectors.counting()));

        for (Category requiredCategory : requiredCategories) {
            if (!categoryCounts.containsKey(requiredCategory.getCategoryCode()) || 
                categoryCounts.get(requiredCategory.getCategoryCode()) == 0) {
                result.missingRequiredCategories.add(String.format("%s (%s)", 
                        requiredCategory.getCategoryCode(), requiredCategory.getCategoryName()));
                result.errors.add(String.format("Required category '%s' has no line items assigned", 
                        requiredCategory.getCategoryCode()));
            }
        }

        // Determine overall validation status
        result.isValid = result.errors.isEmpty();
        
        return result;
    }

    private String formatValidationResult(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        
        if (result.isValid) {
            sb.append("✓ VALIDATION PASSED\n\n");
            sb.append(String.format("Invoice %s (ID: %d) is complete and valid.\n", 
                    result.invoiceNumber, result.invoiceId));
            sb.append("All required fields are present:\n");
            sb.append("  - Service period: Set\n");
            sb.append("  - Required metadata: Complete\n");
            sb.append("  - Line item categories: All assigned\n");
            sb.append("  - Required categories: All covered\n");
        } else {
            sb.append("✗ VALIDATION FAILED\n\n");
            sb.append(String.format("Invoice %s (ID: %d) has validation errors:\n\n", 
                    result.invoiceNumber, result.invoiceId));
            
            if (result.missingServicePeriod) {
                sb.append("▸ SERVICE PERIOD MISSING\n");
                sb.append("  Action: Set service period start and end dates\n\n");
            }
            
            if (!result.missingMetadataKeys.isEmpty()) {
                sb.append(String.format("▸ MISSING REQUIRED METADATA (%d):\n", 
                        result.missingMetadataKeys.size()));
                for (String key : result.missingMetadataKeys) {
                    sb.append(String.format("  - %s\n", key));
                }
                sb.append("  Action: Use storeInvoiceMetadata() to add these keys\n\n");
            }
            
            if (!result.uncategorizedLineItems.isEmpty()) {
                sb.append(String.format("▸ UNCATEGORIZED LINE ITEMS (%d):\n", 
                        result.uncategorizedLineItems.size()));
                for (String item : result.uncategorizedLineItems) {
                    sb.append(String.format("  - %s\n", item));
                }
                sb.append("  Action: Use updateLineItemCategory() to categorize these items\n\n");
            }
            
            if (!result.missingRequiredCategories.isEmpty()) {
                sb.append(String.format("▸ MISSING REQUIRED CATEGORIES (%d):\n", 
                        result.missingRequiredCategories.size()));
                for (String category : result.missingRequiredCategories) {
                    sb.append(String.format("  - %s\n", category));
                }
                sb.append("  Action: Ensure at least one line item is assigned to each required category\n\n");
            }
            
            sb.append(String.format("Total errors: %d\n", result.errors.size()));
            sb.append("\nPlease fix these issues before completing the invoice processing.");
        }
        
        return sb.toString();
    }

    private static class ValidationResult {
        Long invoiceId;
        String invoiceNumber;
        boolean isValid = true;
        boolean missingServicePeriod = false;
        List<String> missingMetadataKeys = new ArrayList<>();
        List<String> uncategorizedLineItems = new ArrayList<>();
        List<String> missingRequiredCategories = new ArrayList<>();
        List<String> errors = new ArrayList<>();
    }
}

