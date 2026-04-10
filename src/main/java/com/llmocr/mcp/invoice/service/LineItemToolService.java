package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem.LineItemCategory;
import com.llmocr.mcp.invoice.dto.CustomerAllocation;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import com.llmocr.mcp.invoice.service.EnergyCostCalculatorService.AllocationMethod;
import com.llmocr.mcp.invoice.service.EnergyCostCalculatorService.CustomerUsage;
import com.llmocr.mcp.invoice.service.LineItemCategorizationService.CategorizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Tools for Invoice Line Item Management
 * Supports third-party billing audit workflows
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineItemToolService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final CategoryRepository categoryRepository;
    private final LineItemCategorizationService categorizationService;
    private final EnergyCostCalculatorService costCalculatorService;
    private final ObjectMapper objectMapper;

    /**
     * Tool 1: Add a line item to an invoice with automatic categorization
     */
    @Tool(description = "Add a line item to an invoice. Line numbers are AUTO-GENERATED sequentially. " +
                        "Category is REQUIRED - call getAvailableCategories() first to see valid category codes for this tenant. " +
                        "IMPORTANT: All numeric values (lineTotal, quantity, unitPrice) must be sent as STRINGS, not numbers.")
    @Transactional
    public String addInvoiceLineItem(
            @ToolParam(description = "Invoice ID to add line item to", required = true) Long invoiceId,
            @ToolParam(description = "Line item description text", required = true) String description,
            @ToolParam(description = "Line total amount AS STRING (e.g. \"45.50\", \"329.67\"). Do NOT send as number.", required = true) String lineTotal,
            @ToolParam(description = "Category code from tenant's available categories (REQUIRED). Call getAvailableCategories() to get valid codes.", required = true) String category,
            @ToolParam(description = "Quantity AS STRING (e.g. \"10\", \"1646\"). Do NOT send as number. Defaults to \"1\" if omitted.", required = false) String quantity,
            @ToolParam(description = "Unit of measure (e.g. \"kWh\", \"units\", \"gallons\")", required = false) String unitOfMeasure,
            @ToolParam(description = "Unit price AS STRING (e.g. \"4.55\", \"0.2\"). Do NOT send as number.", required = false) String unitPrice) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();
            String userId = McpSecurityContext.getCurrentUserId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            // Validate required parameters are provided as strings
            if (lineTotal == null || lineTotal.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Parameter 'lineTotal' is required and must be a STRING. " +
                    "Example: Call with lineTotal=\"329.67\" (with quotes), NOT lineTotal=329.67 (without quotes).");
            }
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter 'description' is required and cannot be empty.");
            }
            if (category == null || category.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Parameter 'category' is required. " +
                    "Call getAvailableCategories() tool first to get valid category codes for this tenant.");
            }

            // Verify invoice exists and belongs to tenant
            Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

            // Auto-generate sequential line number (10, 20, 30, ...)
            Integer lineNumber = lineItemRepository.findMaxLineNumberByInvoiceId(invoiceId)
                    .orElse(0) + 10;

            log.info("Adding line item #{} to invoice {} for tenant {}", lineNumber, invoiceId, tenantId);

            // Parse amounts with resilient handling
            BigDecimal lineTotalAmount = parseCurrencyAmount(lineTotal);
            BigDecimal qty = parseNumericAmount(quantity, "quantity");
            BigDecimal price = parseNumericAmount(unitPrice, "unitPrice");

            // CRITICAL: lineTotal is AUTHORITATIVE and must NEVER be recalculated from qty × price
            log.debug("Adding line item - preserving lineTotal={} (qty={}, price={})", lineTotalAmount, qty, price);

            // Create line item
            InvoiceLineItem.InvoiceLineItemBuilder builder = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .lineNumber(lineNumber)  // Auto-generated sequential line number
                    .description(description)
                    .lineTotal(lineTotalAmount)  // USER'S VALUE - PRESERVE AS-IS
                    .quantity(qty)
                    .unitPrice(price)
                    .unitOfMeasure(unitOfMeasure);

            // Parse energy-specific fields if present
            if ("KWH".equalsIgnoreCase(unitOfMeasure) || "kWh".equalsIgnoreCase(unitOfMeasure)) {
                builder.energyUnit(unitOfMeasure);
                builder.energyQuantity(qty);
                if (price != null) {
                    builder.energyRate(price);
                }
            }

            InvoiceLineItem lineItem = builder.build();

            // GUARD: Verify lineTotal is still the user's value (not recalculated by builder or JPA)
            if (!lineItem.getLineTotal().equals(lineTotalAmount)) {
                log.warn("UNEXPECTED: lineTotal was modified during build! Original={}, Current={}. Restoring...", 
                        lineTotalAmount, lineItem.getLineTotal());
                lineItem.setLineTotal(lineTotalAmount);  // Force restore the user's value
            }

            // Validate and set the category (now required)
            String categoryCode = category.trim().toUpperCase();
            Category validCategory = categoryRepository
                    .findByTenantIdAndCategoryCode(tenantId, categoryCode)
                    .orElseThrow(() -> {
                        // Category doesn't exist - return eligible categories in error
                        List<Category> eligibleCategories = categoryRepository
                                .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCategoryNameAsc(tenantId);
                        
                        String categoryList = eligibleCategories.stream()
                                .map(c -> c.getCategoryCode() + " (" + c.getCategoryName() + ")")
                                .collect(Collectors.joining(", "));
                        
                        return new IllegalArgumentException(
                                "Invalid category '" + categoryCode + "' for tenant '" + tenantId + "'. " +
                                "Eligible categories: " + categoryList);
                    });
            
            if (!validCategory.getIsActive()) {
                throw new IllegalArgumentException("Category '" + categoryCode + "' is not active");
            }
            
            // Set the category provided by AI
            lineItem.setCategory(categoryCode);
            lineItem.setCategoryConfidence(BigDecimal.ONE); // 100% confidence for manual categorization
            lineItem.setCategorizedBy(InvoiceLineItem.CategorizationMethod.USER);
            lineItem.setRequiresReview(false);
            
            CategorizationResult categorization = new CategorizationResult(
                categoryCode,
                BigDecimal.ONE,
                InvoiceLineItem.CategorizationMethod.USER,
                "Category explicitly provided by AI",
                new HashMap<>() // Empty alternatives map for manual categorization
            );

            // Save line item
            InvoiceLineItem saved = lineItemRepository.save(lineItem);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("lineItemId", saved.getId());
            response.put("suggestedCategory", categorization.category()); // category is already String
            response.put("confidence", categorization.confidence());
            response.put("reasoning", categorization.reasoning());
            response.put("requiresReview", saved.getRequiresReview());

            return objectMapper.writeValueAsString(response);

        } catch (NumberFormatException e) {
            log.error("Failed to parse numeric value: {}", e.getMessage(), e);
            throw new IllegalArgumentException(
                "Invalid number format. " +
                "Make sure lineTotal, quantity, and unitPrice are sent as STRINGS (in quotes). " +
                "Example: lineTotal=\"329.67\" NOT lineTotal=329.67. " +
                "Original error: " + e.getMessage(), e);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("Failed to parse JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException(
                "JSON parsing failed. This usually means you sent a NUMBER instead of a STRING. " +
                "All numeric parameters (lineTotal, quantity, unitPrice) MUST be strings. " +
                "Example: {\"lineTotal\":\"329.67\",\"quantity\":\"1646\",\"unitPrice\":\"0.2\"} " +
                "NOT {\"lineTotal\":329.67,\"quantity\":1646,\"unitPrice\":0.2}. " +
                "Original error: " + e.getMessage(), e);
        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is (already have good messages)
            throw e;
        } catch (Exception e) {
            log.error("Failed to add line item: {}", e.getMessage(), e);
            String errorMsg = e.getMessage();
            // Detect common type conversion errors
            if (errorMsg != null && (errorMsg.contains("cannot be converted") || 
                                    errorMsg.contains("Character array") || 
                                    errorMsg.contains("exponential mark"))) {
                throw new IllegalArgumentException(
                    "Type conversion error - you likely sent a NUMBER instead of a STRING. " +
                    "Parameters lineTotal, quantity, and unitPrice must be STRINGS (in quotes). " +
                    "Correct: {\"lineTotal\":\"329.67\",\"quantity\":\"1646\",\"unitPrice\":\"0.2\"} " +
                    "Wrong: {\"lineTotal\":329.67,\"quantity\":1646,\"unitPrice\":0.2}. " +
                    "Fix: Add quotes around all numeric values. Original error: " + errorMsg, e);
            }
            throw new RuntimeException("Failed to add line item: " + errorMsg, e);
        }
    }

    /**
     * Tool 2: Categorize an existing line item
     */
    @Tool(description = "Re-categorize an EXISTING line item using rule-based classification. " +
                        "NOTE: Use updateLineItemCategory() instead to manually set a category. " +
                        "This tool only applies tenant-specific rules and may return null if no rules match.")
    @Transactional
    public String categorizeLineItem(
            @ToolParam(description = "Line item ID to categorize (required)", required = true) Long lineItemId) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            // Validate lineItemId is not null
            if (lineItemId == null) {
                throw new IllegalArgumentException("lineItemId parameter is required and cannot be null");
            }

            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .orElseThrow(() -> new IllegalArgumentException("Line item not found"));

            // Verify tenant access
            if (!lineItem.getInvoice().getTenantId().equals(tenantId)) {
                throw new SecurityException("Access denied to line item from different tenant");
            }

            // Categorize
            CategorizationResult result = categorizationService.categorizeLineItem(lineItem);

            // Update line item
            lineItem.setCategory(result.category());
            lineItem.setCategoryConfidence(result.confidence());
            lineItem.setCategorizedBy(result.method());
            lineItem.setRequiresReview(result.confidence().compareTo(new BigDecimal("0.85")) < 0);
            lineItemRepository.save(lineItem);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("lineItemId", lineItemId);
            response.put("category", result.category()); // category is already String
            response.put("confidence", result.confidence());
            response.put("method", result.method().name());
            response.put("reasoning", result.reasoning());
            response.put("alternativeCategories", result.alternatives());
            response.put("requiresReview", lineItem.getRequiresReview());

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to categorize line item: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to categorize line item: " + e.getMessage(), e);
        }
    }

    /**
     * Tool 3: Update line item category (manual override)
     */
    @Tool(description = "Set or update the category of a line item. Use this to assign categories based on available tenant categories. " +
                        "Call getAvailableCategories() first to see valid category codes for this tenant.")
    @Transactional
    public String updateLineItemCategory(
            @ToolParam(description = "Line item ID to update (required)", required = true) Long lineItemId,
            @ToolParam(description = "Category code from tenant's available categories (required)", required = true) String newCategory,
            @ToolParam(description = "Reason for the category assignment (optional)") String reason) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();
            String userId = McpSecurityContext.getCurrentUserId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            // Validate lineItemId is not null
            if (lineItemId == null || lineItemId <= 0) {
                throw new IllegalArgumentException("lineItemId parameter is required and must be a positive number");
            }

            // Validate newCategory is not null
            if (newCategory == null || newCategory.trim().isEmpty()) {
                throw new IllegalArgumentException("newCategory parameter is required and cannot be null or empty");
            }

            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .orElseThrow(() -> new IllegalArgumentException("Line item not found"));

            // Verify tenant access
            if (!lineItem.getInvoice().getTenantId().equals(tenantId)) {
                throw new SecurityException("Access denied to line item from different tenant");
            }

            // Store original for learning
            String originalCategory = lineItem.getCategory();
            BigDecimal originalConfidence = lineItem.getCategoryConfidence();

            // CRITICAL: Validate that the category exists for this tenant!
            Category dbCategory = categoryRepository.findByTenantIdAndCategoryCode(tenantId, newCategory.toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Category '" + newCategory + "' does not exist for this tenant or is not active"));
            
            if (!dbCategory.getIsActive()) {
                throw new IllegalArgumentException("Category '" + newCategory + "' is not active");
            }

            // Update category
            String newCat = newCategory.toUpperCase();
            lineItem.setCategory(newCat);
            lineItem.setCategoryConfidence(new BigDecimal("1.00")); // User override is 100% confident
            lineItem.setCategorizedBy(InvoiceLineItem.CategorizationMethod.USER);
            lineItem.setRequiresReview(false);
            lineItem.setReviewedBy(userId);
            lineItem.setReviewedAt(java.time.LocalDateTime.now());
            lineItemRepository.save(lineItem);

            // Learn from correction
            if (originalCategory != null && !originalCategory.equals(newCat)) {
                categorizationService.learnFromCorrection(
                        lineItemId,
                        originalCategory,
                        originalConfidence,
                        newCat,
                        reason,
                        lineItem.getDescription(),
                        lineItem.getLineTotal()
                );
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("lineItemId", lineItemId);
            response.put("updatedCategory", newCat); // newCat is already String
            response.put("reviewedBy", userId);

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to update line item category: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update line item category: " + e.getMessage(), e);
        }
    }

    /**
     * Tool 4: Get line items by category
     */
    @Tool(description = "Get all line items for an invoice filtered by category. Useful for reviewing specific types of charges.")
    @Transactional(readOnly = true)
    public String getLineItemsByCategory(
            @ToolParam(description = "Invoice ID (required)") Long invoiceId,
            @ToolParam(description = "Category code to filter by (required)") String category) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            // Verify invoice access
            Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

            // Validate category exists for tenant
            Category dbCategory = categoryRepository.findByTenantIdAndCategoryCode(tenantId, category.toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Category '" + category + "' does not exist for this tenant"));

            List<InvoiceLineItem> items = lineItemRepository.findByInvoiceIdAndCategory(invoiceId, category.toUpperCase());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("category", category);
            response.put("count", items.size());
            response.put("lineItems", items);

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to get line items by category: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get line items by category: " + e.getMessage(), e);
        }
    }

    /**
     * Tool 5: Calculate pass-through total
     */
    @Tool(description = "Calculate total pass-through charges for an invoice. Returns breakdown by category and per-kWh rates.")
    @Transactional(readOnly = true)
    public String calculatePassThroughTotal(
            @ToolParam(description = "Invoice ID to calculate totals for (required)") Long invoiceId) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            // Verify invoice access
            Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

            var summary = costCalculatorService.calculatePassThroughCharges(invoiceId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("totalUsageKWh", summary.totalUsageKWh());
            response.put("passThroughCharges", summary.categoryTotals());
            response.put("totalPassThroughCharges", summary.totalPassThroughCharges());
            response.put("totalGenerationCredits", summary.totalGenerationCredits());
            response.put("netPassThroughAmount", summary.netPassThroughAmount());
            response.put("passThroughPerKWh", summary.passThroughPerKWh());
            response.put("netPerKWh", summary.netPerKWh());

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to calculate pass-through total: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate pass-through total: " + e.getMessage(), e);
        }
    }

    /**
     * Tool 6: Allocate generation credits to customers
     */
    @Tool(description = "Allocate generation credits to customers based on their usage. Supports pro-rata and fixed allocation methods.")
    @Transactional(readOnly = true)
    public String allocateGenerationCredit(
            @ToolParam(description = "Invoice ID containing generation credits (required)", required = true) Long invoiceId,
            @ToolParam(description = "Allocation method: 'pro-rata' or 'fixed' (required)", required = true) String allocationMethodStr,
            @ToolParam(description = "List of customer allocations with customerId and usageKWh (required)", required = true) List<CustomerAllocation> customers) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            if (invoiceId == null) {
                throw new IllegalArgumentException("invoiceId is required");
            }
            
            if (allocationMethodStr == null || allocationMethodStr.trim().isEmpty()) {
                throw new IllegalArgumentException("allocationMethodStr is required");
            }
            
            if (customers == null || customers.isEmpty()) {
                throw new IllegalArgumentException("customers list cannot be null or empty");
            }

            // Verify invoice access
            Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

            // Convert to CustomerUsage objects
            List<CustomerUsage> customerUsages = new ArrayList<>();
            for (CustomerAllocation allocation : customers) {
                if (allocation.getCustomerId() == null || allocation.getCustomerId().trim().isEmpty()) {
                    throw new IllegalArgumentException("customerId is required for all customer allocations");
                }
                if (allocation.getUsageKWh() == null) {
                    throw new IllegalArgumentException("usageKWh is required for all customer allocations");
                }
                customerUsages.add(new CustomerUsage(
                        allocation.getCustomerId(),
                        allocation.getUsageKWh()
                ));
            }

            AllocationMethod method = AllocationMethod.valueOf(allocationMethodStr.toUpperCase());

            var allocation = costCalculatorService.allocateGenerationCredits(invoiceId, customerUsages, method);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("totalCredit", allocation.totalCredit());
            response.put("totalUsageKWh", allocation.totalUsageKWh());
            response.put("creditPerKWh", allocation.creditPerKWh());
            response.put("allocationMethod", method.name());
            response.put("allocations", allocation.allocations());

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            // Re-throw validation and security errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to allocate generation credit: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to allocate generation credit: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to parse currency amounts
     */
    private BigDecimal parseCurrencyAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }

        try {
            String trimmed = amountStr.trim();
            boolean isNegative = false;

            // Check for parentheses notation for negative numbers
            if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                isNegative = true;
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            // Remove currency symbols and whitespace
            String cleanAmount = trimmed
                    .replaceAll("[$€£¥₹]", "")
                    .replaceAll("[,\\s]", "")
                    .trim();

            if (cleanAmount.isEmpty()) {
                throw new IllegalArgumentException("No numeric value found in amount: " + amountStr);
            }

            BigDecimal amount = new BigDecimal(cleanAmount);

            if (isNegative) {
                amount = amount.negate();
            }

            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + amountStr + " - " + e.getMessage());
        }
    }

    /**
     * Helper method to parse numeric amounts (quantity, unitPrice)
     * Defaults: quantity defaults to 1, unitPrice defaults to null if not provided
     */
    private BigDecimal parseNumericAmount(String amountStr, String fieldName) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            // Special handling for defaults based on field type
            if ("quantity".equals(fieldName)) {
                log.debug("Field '{}' is null or empty, defaulting to 1.0", fieldName);
                return BigDecimal.ONE;
            } else if ("unitPrice".equals(fieldName)) {
                log.debug("Field '{}' is null or empty, will remain null (use lineTotal as provided)", fieldName);
                return null;  // Return null instead of ZERO - LLM should provide amount directly
            } else {
                log.warn("Field '{}' is null or empty, defaulting to 0.0", fieldName);
                return BigDecimal.ZERO;
            }
        }

        try {
            String trimmed = amountStr.trim();
            boolean isNegative = false;

            // Check for parentheses notation for negative numbers
            if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                isNegative = true;
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            // Remove currency symbols and whitespace
            String cleanAmount = trimmed
                    .replaceAll("[$€£¥₹]", "")
                    .replaceAll("[,\\s]", "")
                    .trim();

            if (cleanAmount.isEmpty()) {
                // Fall back to defaults
                if ("quantity".equals(fieldName)) {
                    log.warn("No numeric value found in '{}' field: {}, defaulting to 1.0", fieldName, amountStr);
                    return BigDecimal.ONE;
                } else if ("unitPrice".equals(fieldName)) {
                    log.warn("No numeric value found in '{}' field: {}, will remain null", fieldName, amountStr);
                    return null;
                } else {
                    log.warn("No numeric value found in '{}' field: {}", fieldName, amountStr);
                    return BigDecimal.ZERO;
                }
            }

            BigDecimal amount = new BigDecimal(cleanAmount);

            if (isNegative) {
                amount = amount.negate();
            }

            return amount;
        } catch (NumberFormatException e) {
            log.error("Invalid '{}' format: {}", fieldName, amountStr + " - " + e.getMessage());
            // Return defaults rather than throwing for optional fields
            if ("quantity".equals(fieldName)) {
                log.warn("Could not parse quantity, using default of 1.0");
                return BigDecimal.ONE;
            } else if ("unitPrice".equals(fieldName)) {
                log.warn("Could not parse unitPrice, using null (rely on lineTotal)");
                return null;
            } else {
                throw new IllegalArgumentException("Invalid " + fieldName + " format: " + amountStr + " - " + e.getMessage());
            }
        }
    }
}

