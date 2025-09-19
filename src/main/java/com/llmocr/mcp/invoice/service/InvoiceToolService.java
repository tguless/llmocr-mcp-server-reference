package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Invoice Tool Service for Stateless MCP Server
 * 
 * Implements invoice processing tools using Spring AI 1.1 @Tool annotation
 * following the official stateless MCP server documentation.
 * 
 * Authentication and user context are handled by the Spring AI MCP framework
 * with OAuth 2.1 compliant Bearer token validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceToolService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceValidationService invoiceValidationService;
    private final McpAuditService mcpAuditService;
    private final ObjectMapper objectMapper;

    /**
     * Process and store a new invoice from extracted data
     * 
     * Uses Spring AI @Tool annotation for automatic MCP tool registration
     * with the stateless MCP server framework.
     */
    @Tool(description = "Process and store a new invoice from extracted data. Returns the created invoice ID or error details.")
    @Transactional
    public String processInvoice(String invoiceNumber, String vendorName, String vendorAddress,
                               String customerName, String invoiceDate, String dueDate,
                               String totalAmount, String currency, String description) {
        
        long startTime = System.currentTimeMillis();
        
        // Get user context from stateless MCP server framework
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        // Validate authentication (handled by Spring AI MCP framework)
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, errorMessage, startTime);
            return "ERROR: " + errorMessage;
        }
        
        try {
            log.info("Processing invoice {} for tenant {} by user {}", invoiceNumber, tenantId, userId);

            // Validate required fields
            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Invoice number is required");
            }
            if (vendorName == null || vendorName.trim().isEmpty()) {
                throw new IllegalArgumentException("Vendor name is required");
            }
            if (totalAmount == null || totalAmount.trim().isEmpty()) {
                throw new IllegalArgumentException("Total amount is required");
            }

            // Check for duplicates (tenant-isolated)
            if (invoiceRepository.existsByTenantIdAndInvoiceNumber(tenantId, invoiceNumber)) {
                String message = String.format("Invoice %s already exists for tenant %s", invoiceNumber, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, message, startTime);
                return "ERROR: " + message;
            }

            // Create invoice entity
            Invoice invoice = Invoice.builder()
                    .tenantId(tenantId)
                    .invoiceNumber(invoiceNumber.trim())
                    .vendorName(vendorName.trim())
                    .vendorAddress(vendorAddress)
                    .customerName(customerName)
                    .invoiceDate(parseDate(invoiceDate))
                    .dueDate(parseDate(dueDate))
                    .totalAmount(parseCurrencyAmount(totalAmount))
                    .currency(currency != null ? currency : "USD")
                    .description(description)
                    .status(Invoice.InvoiceStatus.PENDING)
                    .processingStatus(Invoice.ProcessingStatus.NEW)
                    .createdBy(userId)
                    .build();

            // Validate invoice
            List<String> validationErrors = invoiceValidationService.validateInvoice(invoice);
            if (!validationErrors.isEmpty()) {
                String errorMessage = "Validation failed: " + String.join(", ", validationErrors);
                mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, errorMessage, startTime);
                return "ERROR: " + errorMessage;
            }

            // Save invoice
            Invoice savedInvoice = invoiceRepository.save(invoice);
            
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", true, 
                    "Invoice processed successfully", startTime);

            log.info("Successfully processed invoice {} with ID {} for tenant {}", 
                    invoiceNumber, savedInvoice.getId(), tenantId);

            return String.format("SUCCESS: Invoice processed with ID %d", savedInvoice.getId());

        } catch (Exception e) {
            log.error("Failed to process invoice {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, e.getMessage(), startTime);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Check if an invoice exists by invoice number
     */
    @Tool(description = "Check if an invoice exists by invoice number. Returns true/false or error details.")
    @Transactional(readOnly = true)
    public String checkInvoiceExists(String invoiceNumber) {
        // Get user context from stateless MCP server framework
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        // Validate authentication
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            log.warn("Unauthorized access attempt for checkInvoiceExists: {}", errorMessage);
            return "ERROR: " + errorMessage;
        }
        
        try {
            log.debug("Checking if invoice {} exists for tenant {} by user {}", invoiceNumber, tenantId, userId);

            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Invoice number is required");
            }

            boolean exists = invoiceRepository.existsByTenantIdAndInvoiceNumber(tenantId, invoiceNumber.trim());
            
            log.debug("Invoice existence check completed for {}: {}", invoiceNumber, exists);

            return exists ? "true" : "false";

        } catch (Exception e) {
            log.error("Failed to check invoice existence {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get invoice details by invoice number
     */
    @Tool(description = "Get invoice details by invoice number. Returns JSON invoice data or error details.")
    @Transactional(readOnly = true)
    public String getInvoiceByNumber(String invoiceNumber) {
        long startTime = System.currentTimeMillis();
        
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", false, errorMessage, startTime);
            return "ERROR: " + errorMessage;
        }
        
        try {
            log.debug("Getting invoice {} for tenant {} by user {}", invoiceNumber, tenantId, userId);

            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Invoice number is required");
            }

            var invoiceOpt = invoiceRepository.findByTenantIdAndInvoiceNumber(tenantId, invoiceNumber.trim());
            
            if (invoiceOpt.isEmpty()) {
                mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", true, 
                        "Invoice not found", startTime);
                return "NOT_FOUND: Invoice not found";
            }

            Invoice invoice = invoiceOpt.get();
            String jsonResult = objectMapper.writeValueAsString(invoice);
            
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", true, 
                    "Invoice retrieved successfully", startTime);

            return "SUCCESS: " + jsonResult;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize invoice {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", false, e.getMessage(), startTime);
            return "ERROR: Failed to serialize invoice data";
        } catch (Exception e) {
            log.error("Failed to get invoice {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", false, e.getMessage(), startTime);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get invoice statistics for the current tenant
     */
    @Tool(description = "Get invoice statistics for the current tenant. Returns JSON statistics or error details.")
    @Transactional(readOnly = true)
    public String getInvoiceStatistics() {
        long startTime = System.currentTimeMillis();
        
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceStatistics", false, errorMessage, startTime);
            return "ERROR: " + errorMessage;
        }
        
        try {
            log.debug("Getting invoice statistics for tenant {} by user {}", tenantId, userId);

            long totalCount = invoiceRepository.countByTenantId(tenantId);
            long pendingCount = invoiceRepository.countByTenantIdAndProcessingStatus(tenantId, Invoice.ProcessingStatus.NEW);
            long processedCount = invoiceRepository.countByTenantIdAndProcessingStatus(tenantId, Invoice.ProcessingStatus.COMPLETED);
            
            BigDecimal totalApprovedAmount = invoiceRepository.sumTotalAmountByTenantIdAndStatus(tenantId, Invoice.InvoiceStatus.APPROVED);
            if (totalApprovedAmount == null) totalApprovedAmount = BigDecimal.ZERO;

            String statistics = String.format(
                    "{\"totalInvoices\": %d, \"pendingInvoices\": %d, \"processedInvoices\": %d, \"totalApprovedAmount\": \"%s\"}",
                    totalCount, pendingCount, processedCount, totalApprovedAmount.toString());
            
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceStatistics", true, 
                    "Statistics retrieved successfully", startTime);

            return "SUCCESS: " + statistics;

        } catch (Exception e) {
            log.error("Failed to get invoice statistics for tenant {}: {}", tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceStatistics", false, e.getMessage(), startTime);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Parse currency amount string to BigDecimal, removing currency symbols
     */
    private BigDecimal parseCurrencyAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        
        try {
            // Remove common currency symbols and whitespace
            String cleanAmount = amountStr.trim()
                    .replaceAll("[$€£¥₹]", "")  // Remove currency symbols
                    .replaceAll("[,\\s]", "")   // Remove commas and spaces
                    .trim();
            
            if (cleanAmount.isEmpty()) {
                throw new IllegalArgumentException("No numeric value found in amount: " + amountStr);
            }
            
            log.debug("Parsed currency amount '{}' to '{}'", amountStr, cleanAmount);
            return new BigDecimal(cleanAmount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + amountStr + " - " + e.getMessage());
        }
    }

    /**
     * Parse date string to LocalDate with support for multiple formats
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = dateStr.trim();
        
        // List of supported date formats (most common first)
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),     // 2025-09-01
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),     // 09/01/2025
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),     // 01/09/2025
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),     // 09-01-2025
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),     // 01-09-2025
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),     // 2025/09/01
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),     // 01.09.2025
            DateTimeFormatter.ofPattern("MMM dd, yyyy"),   // Sep 01, 2025
            DateTimeFormatter.ofPattern("dd MMM yyyy"),    // 01 Sep 2025
            DateTimeFormatter.ofPattern("MMMM dd, yyyy"),  // September 01, 2025
        };
        
        // Try each formatter
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // Continue to next formatter
            }
        }
        
        // If all formatters fail, provide helpful error message
        throw new IllegalArgumentException("Invalid date format: '" + dateStr + "'. " +
                "Supported formats: YYYY-MM-DD, MM/DD/YYYY, DD/MM/YYYY, MM-DD-YYYY, DD-MM-YYYY, " +
                "YYYY/MM/DD, DD.MM.YYYY, MMM DD, YYYY, DD MMM YYYY, MMMM DD, YYYY");
    }
}
