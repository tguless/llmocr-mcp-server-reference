package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * MCP Tool for updating invoice fields
 * 
 * Allows the AI to correct invoice data when validation fails or errors are discovered.
 * Particularly important for adding missing service period dates after initial creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUpdateToolService {

    private final InvoiceRepository invoiceRepository;
    private final McpAuditService mcpAuditService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Update invoice fields
     * 
     * Use this tool to correct or add missing invoice data, especially service period dates
     * when validation fails. All parameters except invoiceId are optional - only provide
     * the fields you want to update.
     * 
     * @param invoiceId The invoice ID to update (required)
     * @param serviceStartDate Service period start date (YYYY-MM-DD format, optional)
     * @param serviceEndDate Service period end date (YYYY-MM-DD format, optional)
     * @param invoiceNumber New invoice number (optional)
     * @param totalAmount New total amount (optional)
     * @param dueDate New due date (YYYY-MM-DD format, optional)
     * @return Success message with updated fields
     */
    @Tool(
        name = "updateInvoice",
        description = "Update invoice fields to correct errors or add missing data. " +
                "Use this when validation fails due to missing service period or other required fields. " +
                "All parameters except invoiceId are optional - only provide fields to update. " +
                "Dates must be in YYYY-MM-DD format."
    )
    @Transactional
    public String updateInvoice(
            @ToolParam(description = "Invoice ID to update (required)") Long invoiceId,
            @ToolParam(description = "Service period start date (YYYY-MM-DD format, optional)") String serviceStartDate,
            @ToolParam(description = "Service period end date (YYYY-MM-DD format, optional)") String serviceEndDate,
            @ToolParam(description = "New invoice number (optional)") String invoiceNumber,
            @ToolParam(description = "New total amount (optional)") String totalAmount,
            @ToolParam(description = "New due date (YYYY-MM-DD format, optional)") String dueDate) {
        
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();

        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "updateInvoice", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }

        try {
            // Validate invoice ID
            if (invoiceId == null || invoiceId <= 0) {
                throw new IllegalArgumentException("Valid invoice ID required");
            }

            // Find invoice
            Optional<Invoice> invoiceOpt = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId);
            if (invoiceOpt.isEmpty()) {
                String message = String.format("Invoice %d not found for tenant %s", invoiceId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "updateInvoice", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            Invoice invoice = invoiceOpt.get();
            StringBuilder updatedFields = new StringBuilder();
            int updateCount = 0;

            // Update service period start date
            if (serviceStartDate != null && !serviceStartDate.trim().isEmpty()) {
                try {
                    LocalDate startDate = LocalDate.parse(serviceStartDate, DATE_FORMATTER);
                    invoice.setServicePeriodStartDate(startDate);
                    updatedFields.append("serviceStartDate=").append(serviceStartDate).append(", ");
                    updateCount++;
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid serviceStartDate format. Use YYYY-MM-DD: " + serviceStartDate);
                }
            }

            // Update service period end date
            if (serviceEndDate != null && !serviceEndDate.trim().isEmpty()) {
                try {
                    LocalDate endDate = LocalDate.parse(serviceEndDate, DATE_FORMATTER);
                    invoice.setServicePeriodEndDate(endDate);
                    updatedFields.append("serviceEndDate=").append(serviceEndDate).append(", ");
                    updateCount++;
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid serviceEndDate format. Use YYYY-MM-DD: " + serviceEndDate);
                }
            }

            // Update invoice number
            if (invoiceNumber != null && !invoiceNumber.trim().isEmpty()) {
                invoice.setInvoiceNumber(invoiceNumber);
                updatedFields.append("invoiceNumber=").append(invoiceNumber).append(", ");
                updateCount++;
            }

            // Update total amount
            if (totalAmount != null && !totalAmount.trim().isEmpty()) {
                try {
                    BigDecimal normalizedAmount = parseCurrencyAmount(totalAmount);
                    invoice.setTotalAmount(normalizedAmount);
                    updatedFields.append("totalAmount=").append(normalizedAmount.toPlainString()).append(", ");
                    updateCount++;
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid totalAmount format: " + totalAmount);
                }
            }

            // Update due date
            if (dueDate != null && !dueDate.trim().isEmpty()) {
                try {
                    LocalDate dueDateParsed = LocalDate.parse(dueDate, DATE_FORMATTER);
                    invoice.setDueDate(dueDateParsed);
                    updatedFields.append("dueDate=").append(dueDate).append(", ");
                    updateCount++;
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid dueDate format. Use YYYY-MM-DD: " + dueDate);
                }
            }

            // Check if any updates were made
            if (updateCount == 0) {
                String message = "No updates provided. Specify at least one field to update.";
                mcpAuditService.logOperation("TOOL_CALL", "updateInvoice", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // Set updated by
            invoice.setUpdatedBy(userId);
            
            // Save
            invoiceRepository.save(invoice);

            // Remove trailing comma and space
            String updates = updatedFields.toString();
            if (updates.endsWith(", ")) {
                updates = updates.substring(0, updates.length() - 2);
            }

            String successMessage = String.format("Invoice %d updated successfully. Updated fields: %s", 
                    invoiceId, updates);
            
            log.info("✅ {}", successMessage);
            mcpAuditService.logOperation("TOOL_CALL", "updateInvoice", true, null, startTime);
            
            return successMessage;

        } catch (Exception e) {
            log.error("Failed to update invoice {}: {}", invoiceId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "updateInvoice", false, e.getMessage(), startTime);
            throw new RuntimeException("Failed to update invoice: " + e.getMessage(), e);
        }
    }

    /**
     * Parse currency amount string to BigDecimal, removing currency symbols
     * and handling negative numbers in parentheses notation (e.g., ($100.00) -> -100.00).
     */
    private BigDecimal parseCurrencyAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }

        try {
            String trimmed = amountStr.trim();
            boolean isNegative = false;

            if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                isNegative = true;
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            String cleanAmount = trimmed
                    .replaceAll("[$€£¥₹]", "")
                    .replaceAll("[,\\s]", "")
                    .trim();

            if (cleanAmount.isEmpty()) {
                throw new IllegalArgumentException("No numeric value found in amount: " + amountStr);
            }

            BigDecimal amount = new BigDecimal(cleanAmount);
            return isNegative ? amount.negate() : amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + amountStr + " - " + e.getMessage());
        }
    }
}

