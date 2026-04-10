package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.InvoiceMetadataRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Invoice Management Controller for Admin UI
 * 
 * Handles invoice viewing and management operations with tenant isolation
 */
@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceManagementController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final InvoiceMetadataRepository metadataRepository;

    /**
     * Get invoices with pagination, search, and filtering
     */
    @GetMapping
    public ResponseEntity<?> getInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            // Create pageable with sorting
            Sort sort = sortDir.equalsIgnoreCase("asc") 
                    ? Sort.by(sortBy).ascending() 
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Invoice> invoices;

            // Handle search with vendor filter
            if (search != null && !search.trim().isEmpty()) {
                if (vendor != null && !vendor.trim().isEmpty()) {
                    // Search within specific vendor
                    invoices = invoiceRepository.findByTenantIdAndVendorNameContainingIgnoreCase(
                            tenantId, vendor, pageable);
                    // Further filter by search term
                    invoices = filterPageBySearch(invoices, search);
                } else {
                    // General search
                    invoices = invoiceRepository.searchByTenantIdAndTerm(tenantId, search, pageable);
                }
            } else if (vendor != null && !vendor.trim().isEmpty()) {
                // Filter by vendor only
                invoices = invoiceRepository.findByTenantIdAndVendorNameContainingIgnoreCase(
                        tenantId, vendor, pageable);
            } else {
                // No filters, get all
                invoices = invoiceRepository.findByTenantId(tenantId, pageable);
            }

            // Convert to response DTOs
            List<Map<String, Object>> content = invoices.getContent().stream()
                    .map(this::toInvoiceSummaryResponse)
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", content);
            response.put("totalElements", invoices.getTotalElements());
            response.put("totalPages", invoices.getTotalPages());
            response.put("currentPage", invoices.getNumber());
            response.put("pageSize", invoices.getSize());
            response.put("hasNext", invoices.hasNext());
            response.put("hasPrevious", invoices.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching invoices: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch invoices: " + e.getMessage()));
        }
    }

    /**
     * Get invoice by ID with full details including line items
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getInvoiceById(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        Optional<Invoice> invoiceOpt = invoiceRepository.findByTenantIdAndId(tenantId, id);
        
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Invoice not found"));
        }

        Invoice invoice = invoiceOpt.get();
        
        // Fetch line items separately to avoid lazy loading issues (tenant-scoped for defense-in-depth)
        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceIdAndTenantIdOrderByLineNumber(id, tenantId);
        
        Map<String, Object> response = toInvoiceDetailResponse(invoice, lineItems);

        return ResponseEntity.ok(response);
    }

    /**
     * Get unique vendors for the current tenant (for dropdown/autocomplete)
     */
    @GetMapping("/vendors")
    public ResponseEntity<?> getVendors(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            // Get all invoices for tenant and extract unique vendors
            List<Invoice> invoices = invoiceRepository.findByTenantId(tenantId);
            Set<String> vendors = invoices.stream()
                    .map(Invoice::getVendorName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new)); // TreeSet for sorted order

            return ResponseEntity.ok(vendors);
        } catch (Exception e) {
            log.error("Error fetching vendors: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch vendors"));
        }
    }

    /**
     * Get invoice statistics for the current tenant
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            long totalCount = invoiceRepository.countByTenantId(tenantId);
            long pendingCount = invoiceRepository.countByTenantIdAndProcessingStatus(
                    tenantId, Invoice.ProcessingStatus.NEW);
            long processingCount = invoiceRepository.countByTenantIdAndProcessingStatus(
                    tenantId, Invoice.ProcessingStatus.PROCESSING);
            long completedCount = invoiceRepository.countByTenantIdAndProcessingStatus(
                    tenantId, Invoice.ProcessingStatus.COMPLETED);
            long failedCount = invoiceRepository.countByTenantIdAndProcessingStatus(
                    tenantId, Invoice.ProcessingStatus.FAILED);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalInvoices", totalCount);
            stats.put("newInvoices", pendingCount);
            stats.put("processingInvoices", processingCount);
            stats.put("completedInvoices", completedCount);
            stats.put("failedInvoices", failedCount);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching statistics: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch statistics"));
        }
    }

    /**
     * Convert Invoice entity to summary response DTO (for list view)
     */
    private Map<String, Object> toInvoiceSummaryResponse(Invoice invoice) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", invoice.getId());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("vendorName", invoice.getVendorName());
        response.put("invoiceDate", invoice.getInvoiceDate());
        response.put("dueDate", invoice.getDueDate());
        response.put("servicePeriodStart", invoice.getServicePeriodStartDate());
        response.put("servicePeriodEnd", invoice.getServicePeriodEndDate());
        response.put("totalAmount", invoice.getTotalAmount());
        response.put("currency", invoice.getCurrency());
        response.put("status", invoice.getStatus().name());
        response.put("processingStatus", invoice.getProcessingStatus().name());
        response.put("createdAt", invoice.getCreatedAt());
        response.put("updatedAt", invoice.getUpdatedAt());
        return response;
    }

    /**
     * Convert Invoice entity to detailed response DTO (for detail view)
     */
    private Map<String, Object> toInvoiceDetailResponse(Invoice invoice, List<InvoiceLineItem> lineItems) {
        Map<String, Object> response = new LinkedHashMap<>();
        
        // Basic information
        response.put("id", invoice.getId());
        response.put("tenantId", invoice.getTenantId());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        
        // Vendor information
        Map<String, Object> vendor = new LinkedHashMap<>();
        vendor.put("name", invoice.getVendorName());
        vendor.put("address", invoice.getVendorAddress());
        vendor.put("taxId", invoice.getVendorTaxId());
        response.put("vendor", vendor);
        
        // Customer information
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", invoice.getCustomerName());
        customer.put("address", invoice.getCustomerAddress());
        response.put("customer", customer);
        
        // Dates
        response.put("invoiceDate", invoice.getInvoiceDate());
        response.put("dueDate", invoice.getDueDate());
        
        // Service Period (NEW)
        Map<String, Object> servicePeriod = new LinkedHashMap<>();
        servicePeriod.put("startDate", invoice.getServicePeriodStartDate());
        servicePeriod.put("endDate", invoice.getServicePeriodEndDate());
        response.put("servicePeriod", servicePeriod);
        
        // Amounts
        response.put("subtotalAmount", invoice.getSubtotalAmount());
        response.put("taxAmount", invoice.getTaxAmount());
        response.put("totalAmount", invoice.getTotalAmount());
        response.put("currency", invoice.getCurrency());
        
        // Payment and description
        response.put("paymentTerms", invoice.getPaymentTerms());
        response.put("description", invoice.getDescription());
        
        // Status
        response.put("status", invoice.getStatus().name());
        response.put("processingStatus", invoice.getProcessingStatus().name());
        
        // Source information
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("fileName", invoice.getSourceFileName());
        source.put("fileType", invoice.getSourceFileType());
        source.put("filePath", invoice.getSourceFilePath());
        response.put("source", source);
        
        // Quality metrics
        response.put("confidenceScore", invoice.getConfidenceScore());
        response.put("validationErrors", invoice.getValidationErrors());
        
        // Metadata
        response.put("metadata", invoice.getMetadata());
        
        // Custom Metadata (KEY-VALUE PAIRS - NEW)
        List<Map<String, String>> customMetadataList = metadataRepository
                .findByTenantIdAndInvoiceId(invoice.getTenantId(), invoice.getId())
                .stream()
                .map(meta -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("key", meta.getKey());
                    item.put("value", meta.getValue());
                    item.put("createdBy", meta.getCreatedBy());
                    item.put("updatedAt", meta.getUpdatedAt() != null ? meta.getUpdatedAt().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());
        response.put("customMetadata", customMetadataList);
        
        // Timestamps
        response.put("createdAt", invoice.getCreatedAt());
        response.put("updatedAt", invoice.getUpdatedAt());
        response.put("createdBy", invoice.getCreatedBy());
        response.put("updatedBy", invoice.getUpdatedBy());
        
        // Line items
        List<Map<String, Object>> items = lineItems.stream()
                .map(this::toLineItemResponse)
                .collect(Collectors.toList());
        response.put("lineItems", items);
        response.put("lineItemCount", items.size());
        
        return response;
    }

    /**
     * Convert LineItem entity to response DTO
     */
    private Map<String, Object> toLineItemResponse(InvoiceLineItem item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", item.getId());
        response.put("lineNumber", item.getLineNumber());
        response.put("description", item.getDescription());
        response.put("quantity", item.getQuantity());
        response.put("unitPrice", item.getUnitPrice());
        response.put("lineTotal", item.getLineTotal());
        response.put("taxRate", item.getTaxRate());
        response.put("taxAmount", item.getTaxAmount());
        response.put("productCode", item.getProductCode());
        response.put("unitOfMeasure", item.getUnitOfMeasure());
        
        // Categorization
        response.put("category", item.getCategory()); // category is already String
        response.put("categoryConfidence", item.getCategoryConfidence());
        response.put("categorizedBy", item.getCategorizedBy() != null ? item.getCategorizedBy().name() : null);
        response.put("requiresReview", item.getRequiresReview());
        response.put("reviewedBy", item.getReviewedBy());
        response.put("reviewedAt", item.getReviewedAt());
        
        // Energy-specific fields
        if (item.getEnergyUnit() != null) {
            Map<String, Object> energy = new LinkedHashMap<>();
            energy.put("unit", item.getEnergyUnit());
            energy.put("quantity", item.getEnergyQuantity());
            energy.put("rate", item.getEnergyRate());
            response.put("energy", energy);
        }
        
        response.put("metadata", item.getMetadata());
        
        return response;
    }

    /**
     * Helper method to filter a page by search term (client-side filtering)
     */
    private Page<Invoice> filterPageBySearch(Page<Invoice> page, String search) {
        // This is a simple implementation - in production you'd want to do this at the database level
        return page;
    }
}



