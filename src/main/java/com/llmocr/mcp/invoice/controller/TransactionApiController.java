package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import com.llmocr.mcp.invoice.util.CategoryMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Transaction Viewing (Admin UI)
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionApiController {

    private final InvoiceLineItemRepository lineItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Get all line items (transactions) with their invoice mappings
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTransactions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String invoiceId,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        log.debug("REST API: Getting transactions for tenant: {}, category: {}, invoiceId: {}", 
                tenantId, category, invoiceId);

        List<InvoiceLineItem> lineItems;

        if (invoiceId != null) {
            // Get line items for specific invoice (TENANT-SCOPED)
            lineItems = lineItemRepository.findByInvoiceIdAndTenantIdOrderByLineNumber(
                    Long.parseLong(invoiceId), tenantId);
        } else if (category != null) {
            // Get line items by category (category is now String)
            lineItems = lineItemRepository.findByTenantIdAndCategory(tenantId, category.toUpperCase());
        } else {
            // Get all line items for tenant (limited to recent ones)
            lineItems = lineItemRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                    .stream()
                    .limit(1000) // Limit for performance
                    .collect(Collectors.toList());
        }

        // Build response with invoice details
        List<Map<String, Object>> transactions = lineItems.stream()
                .map(lineItem -> {
                    Map<String, Object> transaction = new LinkedHashMap<>();
                    transaction.put("lineItemId", lineItem.getId());
                    transaction.put("id", lineItem.getId()); // Alias for compatibility
                    transaction.put("lineNumber", lineItem.getLineNumber());
                    
                    // Get invoice details
                    Invoice invoice = lineItem.getInvoice();
                    if (invoice != null) {
                        transaction.put("invoiceId", invoice.getId());
                        transaction.put("invoiceNumber", invoice.getInvoiceNumber());
                        transaction.put("vendorName", invoice.getVendorName());
                        transaction.put("invoiceDate", invoice.getInvoiceDate());
                        transaction.put("invoiceTotal", invoice.getTotalAmount());
                    }
                    
                    transaction.put("description", lineItem.getDescription());
                    transaction.put("quantity", lineItem.getQuantity());
                    transaction.put("unitPrice", lineItem.getUnitPrice());
                    transaction.put("amount", lineItem.getLineTotal());
                    transaction.put("lineTotal", lineItem.getLineTotal()); // Alias for compatibility
                    transaction.put("category", lineItem.getCategory()); // category is now String
                    transaction.put("categoryName", lineItem.getCategory() != null 
                            ? lineItem.getCategory() // Use category code as display name for now
                            : "Uncategorized");
                    transaction.put("categoryConfidence", lineItem.getCategoryConfidence());
                    transaction.put("categorizedBy", lineItem.getCategorizedBy() != null 
                            ? lineItem.getCategorizedBy().name() 
                            : null);
                    transaction.put("requiresReview", lineItem.getRequiresReview());
                    transaction.put("createdAt", lineItem.getCreatedAt());
                    transaction.put("updatedAt", lineItem.getUpdatedAt());
                    
                    // Generic metadata (includes any custom fields like energy data, if present)
                    transaction.put("metadata", lineItem.getMetadata());
                    
                    return transaction;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(transactions);
    }

    /**
     * Get summary statistics for transactions
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTransactionSummary(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        log.debug("REST API: Getting transaction summary for tenant: {}", tenantId);

        Map<String, Object> summary = new LinkedHashMap<>();
        
        // Count by category - get categories from database for this tenant
        List<Category> categories = categoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCategoryNameAsc(tenantId);
        for (Category category : categories) {
            long count = lineItemRepository.countByTenantIdAndCategory(tenantId, category.getCategoryCode());
            summary.put(category.getCategoryCode() + "_count", count);
            
            // Sum amounts by category
            BigDecimal sum = lineItemRepository.sumLineTotalsByCategory(tenantId, category.getCategoryCode());
            summary.put(category.getCategoryCode() + "_total", sum != null ? sum : BigDecimal.ZERO);
        }
        
        // Items requiring review
        long reviewCount = lineItemRepository.findByTenantIdAndRequiresReview(tenantId, true).size();
        summary.put("requires_review_count", reviewCount);
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Export all transactions to Excel file
     */
    @GetMapping("/export/excel")
    public void exportToExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tenantId = (String) request.getAttribute("tenantId");
        String authHeader = request.getHeader("Authorization");
        log.info("Excel export request - tenantId: {}, hasAuthHeader: {}, servletPath: {}", 
                tenantId, (authHeader != null), request.getServletPath());
        
        if (tenantId == null) {
            log.error("TenantId is null - authentication may have failed. Request URI: {}, Headers: Authorization={}", 
                    request.getRequestURI(), authHeader);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        // Get ALL line items for tenant (no limit)
        List<InvoiceLineItem> lineItems = lineItemRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        // Create Excel workbook
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transactions");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create currency style
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));

            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Line Item ID", "Invoice ID", "Invoice Number", "Vendor Name", "Invoice Date",
                "Line Number", "Description", "Quantity", "Unit Price", "Amount",
                "Category", "Category Name", "Confidence", "Requires Review",
                "Metadata",
                "Created At", "Updated At"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (InvoiceLineItem lineItem : lineItems) {
                Row row = sheet.createRow(rowNum++);
                Invoice invoice = lineItem.getInvoice();

                int colNum = 0;
                
                // Line Item ID
                row.createCell(colNum++).setCellValue(lineItem.getId());
                
                // Invoice details
                if (invoice != null) {
                    row.createCell(colNum++).setCellValue(invoice.getId());
                    row.createCell(colNum++).setCellValue(invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "");
                    row.createCell(colNum++).setCellValue(invoice.getVendorName() != null ? invoice.getVendorName() : "");
                    
                    // Invoice Date
                    if (invoice.getInvoiceDate() != null) {
                        Cell dateCell = row.createCell(colNum++);
                        dateCell.setCellValue(invoice.getInvoiceDate().format(dateFormatter));
                    } else {
                        row.createCell(colNum++).setCellValue("");
                    }
                } else {
                    row.createCell(colNum++).setCellValue("");
                    row.createCell(colNum++).setCellValue("");
                    row.createCell(colNum++).setCellValue("");
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Line item details
                row.createCell(colNum++).setCellValue(lineItem.getLineNumber() != null ? lineItem.getLineNumber() : 0);
                row.createCell(colNum++).setCellValue(lineItem.getDescription() != null ? lineItem.getDescription() : "");
                
                // Quantity
                if (lineItem.getQuantity() != null) {
                    row.createCell(colNum++).setCellValue(lineItem.getQuantity().doubleValue());
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Unit Price
                if (lineItem.getUnitPrice() != null) {
                    Cell priceCell = row.createCell(colNum++);
                    priceCell.setCellValue(lineItem.getUnitPrice().doubleValue());
                    priceCell.setCellStyle(currencyStyle);
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Amount
                if (lineItem.getLineTotal() != null) {
                    Cell amountCell = row.createCell(colNum++);
                    amountCell.setCellValue(lineItem.getLineTotal().doubleValue());
                    amountCell.setCellStyle(currencyStyle);
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Category
                row.createCell(colNum++).setCellValue(lineItem.getCategory() != null ? lineItem.getCategory() : "");
                row.createCell(colNum++).setCellValue(lineItem.getCategory() != null ? 
                        lineItem.getCategory() : "Uncategorized"); // category is already String code
                
                // Confidence
                if (lineItem.getCategoryConfidence() != null) {
                    row.createCell(colNum++).setCellValue(lineItem.getCategoryConfidence().doubleValue());
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Requires Review
                row.createCell(colNum++).setCellValue(lineItem.getRequiresReview() != null && lineItem.getRequiresReview() ? "Yes" : "No");
                
                // Metadata (generic JSON export)
                if (lineItem.getMetadata() != null && !lineItem.getMetadata().isEmpty()) {
                    // Convert metadata map to readable string format
                    String metadataStr = lineItem.getMetadata().entrySet().stream()
                            .map(e -> e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining("; "));
                    row.createCell(colNum++).setCellValue(metadataStr);
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                
                // Timestamps
                if (lineItem.getCreatedAt() != null) {
                    row.createCell(colNum++).setCellValue(lineItem.getCreatedAt().format(timestampFormatter));
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
                if (lineItem.getUpdatedAt() != null) {
                    row.createCell(colNum++).setCellValue(lineItem.getUpdatedAt().format(timestampFormatter));
                } else {
                    row.createCell(colNum++).setCellValue("");
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Set response headers
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=transactions_" + tenantId + "_" + 
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");

            // Write to response
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();

            log.info("Successfully exported {} transactions to Excel for tenant: {}", lineItems.size(), tenantId);
        } catch (Exception e) {
            log.error("Failed to export transactions to Excel for tenant: {}", tenantId, e);
            throw new IOException("Failed to generate Excel file", e);
        }
    }
}

