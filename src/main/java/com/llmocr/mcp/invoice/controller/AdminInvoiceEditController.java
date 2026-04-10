package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.InvoiceMetadata;
import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceMetadataRepository;
import com.llmocr.mcp.invoice.repository.MetadataKeyDefinitionRepository;
import com.llmocr.mcp.invoice.service.MetadataValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Admin Invoice Editing
 * Provides endpoints for editing invoice values, service periods, metadata, categories, and line items
 */
@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminInvoiceEditController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMetadataRepository metadataRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final MetadataKeyDefinitionRepository metadataKeyRepository;
    private final MetadataValidationService validationService;

    // ============ SERVICE PERIOD ENDPOINTS ============

    /**
     * Update invoice service period
     */
    @PutMapping("/{invoiceId}/service-period")
    public ResponseEntity<?> updateServicePeriod(
            @PathVariable Long invoiceId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String createdBy = (String) httpRequest.getAttribute("userId");

            log.info("Updating service period for invoice {} in tenant {}", invoiceId, tenantId);

            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            // Update service period dates
            if (request.containsKey("startDate") && !request.get("startDate").isEmpty()) {
                invoice.setServicePeriodStartDate(LocalDate.parse(request.get("startDate")));
            }
            if (request.containsKey("endDate") && !request.get("endDate").isEmpty()) {
                invoice.setServicePeriodEndDate(LocalDate.parse(request.get("endDate")));
            }

            invoice.setUpdatedBy(createdBy);
            invoiceRepository.save(invoice);

            log.info("Successfully updated service period for invoice {}", invoiceId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Service period updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update service period for invoice {}: {}", invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to update service period: " + e.getMessage()
            ));
        }
    }

    /**
     * Update invoice number
     */
    @PutMapping("/{invoiceId}/invoice-number")
    public ResponseEntity<?> updateInvoiceNumber(
            @PathVariable Long invoiceId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String updatedBy = (String) httpRequest.getAttribute("userId");

            log.info("Updating invoice number for invoice {} in tenant {}", invoiceId, tenantId);

            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            String invoiceNumber = request.get("invoiceNumber");
            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Invoice number is required"
                ));
            }

            invoice.setInvoiceNumber(invoiceNumber.trim());
            invoice.setUpdatedBy(updatedBy);
            invoiceRepository.save(invoice);

            log.info("Successfully updated invoice number for invoice {} to '{}'", invoiceId, invoiceNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Invoice number updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update invoice number for invoice {}: {}", invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to update invoice number: " + e.getMessage()
            ));
        }
    }

    // ============ METADATA ENDPOINTS ============

    /**
     * Add new metadata to invoice
     */
    @PostMapping("/{invoiceId}/metadata")
    public ResponseEntity<?> addMetadata(
            @PathVariable Long invoiceId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String createdBy = (String) httpRequest.getAttribute("userId");

            log.info("Adding metadata to invoice {} in tenant {}", invoiceId, tenantId);

            // Verify invoice exists
            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            String key = request.get("key");
            String value = request.get("value");

            if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Key and value are required"
                ));
            }

            // Validate metadata key and value
            String normalizedKey = key.toLowerCase().trim();
            Optional<MetadataKeyDefinition> keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, normalizedKey);
            
            if (keyDefOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Invalid metadata key: '" + key + "' is not defined for this tenant"
                ));
            }

            MetadataKeyDefinition keyDef = keyDefOpt.get();
            MetadataValidationService.ValidationResult validationResult = validationService.validate(keyDef, value.trim());
            if (!validationResult.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", validationResult.getErrorMessage() + " Expected: " + keyDef.getDataType() + ". Example: " + keyDef.getExampleValue()
                ));
            }

            InvoiceMetadata metadata = InvoiceMetadata.builder()
                    .tenantId(tenantId)
                    .invoiceId(invoiceId)
                    .key(normalizedKey)
                    .value(value.trim())
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();

            metadataRepository.save(metadata);

            log.info("Successfully added metadata to invoice {}", invoiceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Metadata added successfully",
                    "id", metadata.getId()
            ));

        } catch (Exception e) {
            log.error("Failed to add metadata to invoice {}: {}", invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to add metadata: " + e.getMessage()
            ));
        }
    }

    /**
     * Update existing metadata
     */
    @PutMapping("/{invoiceId}/metadata/{metadataId}")
    public ResponseEntity<?> updateMetadata(
            @PathVariable Long invoiceId,
            @PathVariable Long metadataId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String updatedBy = (String) httpRequest.getAttribute("userId");

            log.info("Updating metadata {} for invoice {} in tenant {}", metadataId, invoiceId, tenantId);

            InvoiceMetadata metadata = metadataRepository.findById(metadataId)
                    .orElseThrow(() -> new RuntimeException("Metadata not found"));

            // Verify invoice and tenant match
            if (!metadata.getInvoiceId().equals(invoiceId) || !metadata.getTenantId().equals(tenantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "error", "Unauthorized"
                ));
            }

            String key = request.get("key");
            String value = request.get("value");

            if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Key and value are required"
                ));
            }

            // Validate metadata key and value
            String normalizedKey = key.toLowerCase().trim();
            Optional<MetadataKeyDefinition> keyDefOpt = metadataKeyRepository.findByTenantIdAndKeyCode(tenantId, normalizedKey);
            
            if (keyDefOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Invalid metadata key: '" + key + "' is not defined for this tenant"
                ));
            }

            MetadataKeyDefinition keyDef = keyDefOpt.get();
            MetadataValidationService.ValidationResult validationResult = validationService.validate(keyDef, value.trim());
            if (!validationResult.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", validationResult.getErrorMessage() + " Expected: " + keyDef.getDataType() + ". Example: " + keyDef.getExampleValue()
                ));
            }

            metadata.setKey(normalizedKey);
            metadata.setValue(value.trim());
            metadata.setUpdatedBy(updatedBy);

            metadataRepository.save(metadata);

            log.info("Successfully updated metadata {} for invoice {}", metadataId, invoiceId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Metadata updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update metadata {} for invoice {}: {}", metadataId, invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to update metadata: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete metadata
     */
    @DeleteMapping("/{invoiceId}/metadata/{metadataId}")
    public ResponseEntity<?> deleteMetadata(
            @PathVariable Long invoiceId,
            @PathVariable Long metadataId,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");

            log.info("Deleting metadata {} for invoice {} in tenant {}", metadataId, invoiceId, tenantId);

            InvoiceMetadata metadata = metadataRepository.findById(metadataId)
                    .orElseThrow(() -> new RuntimeException("Metadata not found"));

            // Verify invoice and tenant match
            if (!metadata.getInvoiceId().equals(invoiceId) || !metadata.getTenantId().equals(tenantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "error", "Unauthorized"
                ));
            }

            metadataRepository.deleteById(metadataId);

            log.info("Successfully deleted metadata {} for invoice {}", metadataId, invoiceId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Metadata deleted successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to delete metadata {} for invoice {}: {}", metadataId, invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to delete metadata: " + e.getMessage()
            ));
        }
    }

    // ============ LINE ITEM CATEGORY ENDPOINTS ============

    /**
     * Update line item category
     */
    @PutMapping("/{invoiceId}/line-items/{lineItemId}/category")
    public ResponseEntity<?> updateLineItemCategory(
            @PathVariable Long invoiceId,
            @PathVariable Long lineItemId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String updatedBy = (String) httpRequest.getAttribute("userId");

            log.info("Updating category for line item {} in invoice {} for tenant {}", lineItemId, invoiceId, tenantId);

            // Fetch and validate line item belongs to tenant in one query (prevents timing attacks)
            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .filter(li -> li.getInvoice().getId().equals(invoiceId) && 
                                 li.getInvoice().getTenantId().equals(tenantId))
                    .orElseThrow(() -> new RuntimeException("Line item not found or access denied"));

            String category = request.get("category");
            if (category == null || category.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Category is required"
                ));
            }

            // Category is now String, not enum
            lineItem.setCategory(category.toUpperCase());
            lineItemRepository.save(lineItem);

            log.info("Successfully updated category for line item {}", lineItemId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Category updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update category for line item {}: {}", lineItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to update category: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete line item category
     */
    @DeleteMapping("/{invoiceId}/line-items/{lineItemId}/category")
    public ResponseEntity<?> deleteLineItemCategory(
            @PathVariable Long invoiceId,
            @PathVariable Long lineItemId,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String updatedBy = (String) httpRequest.getAttribute("userId");

            log.info("Deleting category for line item {} in invoice {} for tenant {}", lineItemId, invoiceId, tenantId);

            // Fetch and validate line item belongs to tenant in one query (prevents timing attacks)
            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .filter(li -> li.getInvoice().getId().equals(invoiceId) && 
                                 li.getInvoice().getTenantId().equals(tenantId))
                    .orElseThrow(() -> new RuntimeException("Line item not found or access denied"));

            lineItem.setCategory(null);
            lineItem.setCategoryConfidence(null);
            lineItemRepository.save(lineItem);

            log.info("Successfully deleted category for line item {}", lineItemId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Category deleted successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to delete category for line item {}: {}", lineItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to delete category: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete line item
     */
    @DeleteMapping("/{invoiceId}/line-items/{lineItemId}")
    public ResponseEntity<?> deleteLineItem(
            @PathVariable Long invoiceId,
            @PathVariable Long lineItemId,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");

            log.info("Deleting line item {} in invoice {} for tenant {}", lineItemId, invoiceId, tenantId);

            // Fetch and validate line item belongs to tenant in one query (prevents timing attacks)
            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .filter(li -> li.getInvoice().getId().equals(invoiceId) && 
                                 li.getInvoice().getTenantId().equals(tenantId))
                    .orElseThrow(() -> new RuntimeException("Line item not found or access denied"));

            lineItemRepository.deleteById(lineItemId);

            log.info("Successfully deleted line item {}", lineItemId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Line item deleted successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to delete line item {}: {}", lineItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to delete line item: " + e.getMessage()
            ));
        }
    }

    // ============ LINE ITEM CRUD ENDPOINTS ============

    /**
     * Create new line item
     */
    @PostMapping("/{invoiceId}/line-items")
    public ResponseEntity<?> createLineItem(
            @PathVariable Long invoiceId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String createdBy = (String) httpRequest.getAttribute("userId");

            log.info("Creating line item for invoice {} in tenant {}", invoiceId, tenantId);

            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            // Get the highest line number and add 1
            Integer nextLineNumber = invoice.getLineItems().stream()
                    .map(InvoiceLineItem::getLineNumber)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            // Parse required fields
            String description = (String) request.get("description");
            Object lineTotalObj = request.get("lineTotal");
            
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Description is required"
                ));
            }

            if (lineTotalObj == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Line total is required"
                ));
            }

            java.math.BigDecimal lineTotal;
            try {
                if (lineTotalObj instanceof Number) {
                    lineTotal = java.math.BigDecimal.valueOf(((Number) lineTotalObj).doubleValue());
                } else {
                    lineTotal = new java.math.BigDecimal(lineTotalObj.toString());
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "error", "Invalid line total value"
                ));
            }

            // Parse optional fields
            java.math.BigDecimal quantity = null;
            java.math.BigDecimal unitPrice = null;
            java.math.BigDecimal taxRate = null;
            java.math.BigDecimal taxAmount = null;

            if (request.containsKey("quantity") && request.get("quantity") != null) {
                try {
                    Object qtyObj = request.get("quantity");
                    quantity = qtyObj instanceof Number ? 
                            java.math.BigDecimal.valueOf(((Number) qtyObj).doubleValue()) :
                            new java.math.BigDecimal(qtyObj.toString());
                } catch (Exception e) {
                    log.warn("Invalid quantity: {}", request.get("quantity"));
                }
            }

            if (request.containsKey("unitPrice") && request.get("unitPrice") != null) {
                try {
                    Object priceObj = request.get("unitPrice");
                    unitPrice = priceObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()) :
                            new java.math.BigDecimal(priceObj.toString());
                } catch (Exception e) {
                    log.warn("Invalid unit price: {}", request.get("unitPrice"));
                }
            }

            if (request.containsKey("taxRate") && request.get("taxRate") != null) {
                try {
                    Object taxRateObj = request.get("taxRate");
                    taxRate = taxRateObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) taxRateObj).doubleValue()) :
                            new java.math.BigDecimal(taxRateObj.toString());
                } catch (Exception e) {
                    log.warn("Invalid tax rate: {}", request.get("taxRate"));
                }
            }

            if (request.containsKey("taxAmount") && request.get("taxAmount") != null) {
                try {
                    Object taxAmtObj = request.get("taxAmount");
                    taxAmount = taxAmtObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) taxAmtObj).doubleValue()) :
                            new java.math.BigDecimal(taxAmtObj.toString());
                } catch (Exception e) {
                    log.warn("Invalid tax amount: {}", request.get("taxAmount"));
                }
            }

            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .lineNumber(nextLineNumber)
                    .description(description.trim())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .taxRate(taxRate)
                    .taxAmount(taxAmount)
                    .productCode((String) request.get("productCode"))
                    .unitOfMeasure((String) request.get("unitOfMeasure"))
                    .requiresReview(false)
                    .build();

            lineItemRepository.save(lineItem);

            log.info("Successfully created line item {} for invoice {}", lineItem.getId(), invoiceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Line item created successfully",
                    "id", lineItem.getId()
            ));

        } catch (Exception e) {
            log.error("Failed to create line item for invoice {}: {}", invoiceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to create line item: " + e.getMessage()
            ));
        }
    }

    /**
     * Update existing line item
     */
    @PutMapping("/{invoiceId}/line-items/{lineItemId}")
    public ResponseEntity<?> updateLineItem(
            @PathVariable Long invoiceId,
            @PathVariable Long lineItemId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String updatedBy = (String) httpRequest.getAttribute("userId");

            log.info("Updating line item {} in invoice {} for tenant {}", lineItemId, invoiceId, tenantId);

            // Fetch and validate line item belongs to tenant in one query (prevents timing attacks)
            InvoiceLineItem lineItem = lineItemRepository.findById(lineItemId)
                    .filter(li -> li.getInvoice().getId().equals(invoiceId) && 
                                 li.getInvoice().getTenantId().equals(tenantId))
                    .orElseThrow(() -> new RuntimeException("Line item not found or access denied"));

            // Update fields if provided
            if (request.containsKey("description") && request.get("description") != null) {
                String desc = ((String) request.get("description")).trim();
                if (!desc.isEmpty()) {
                    lineItem.setDescription(desc);
                }
            }

            if (request.containsKey("quantity") && request.get("quantity") != null) {
                try {
                    Object qtyObj = request.get("quantity");
                    lineItem.setQuantity(qtyObj instanceof Number ? 
                            java.math.BigDecimal.valueOf(((Number) qtyObj).doubleValue()) :
                            new java.math.BigDecimal(qtyObj.toString()));
                } catch (Exception e) {
                    log.warn("Invalid quantity: {}", request.get("quantity"));
                }
            }

            if (request.containsKey("unitPrice") && request.get("unitPrice") != null) {
                try {
                    Object priceObj = request.get("unitPrice");
                    lineItem.setUnitPrice(priceObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()) :
                            new java.math.BigDecimal(priceObj.toString()));
                } catch (Exception e) {
                    log.warn("Invalid unit price: {}", request.get("unitPrice"));
                }
            }

            if (request.containsKey("lineTotal") && request.get("lineTotal") != null) {
                try {
                    Object totalObj = request.get("lineTotal");
                    lineItem.setLineTotal(totalObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) totalObj).doubleValue()) :
                            new java.math.BigDecimal(totalObj.toString()));
                } catch (Exception e) {
                    log.warn("Invalid line total: {}", request.get("lineTotal"));
                }
            }

            if (request.containsKey("taxRate") && request.get("taxRate") != null) {
                try {
                    Object taxRateObj = request.get("taxRate");
                    lineItem.setTaxRate(taxRateObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) taxRateObj).doubleValue()) :
                            new java.math.BigDecimal(taxRateObj.toString()));
                } catch (Exception e) {
                    log.warn("Invalid tax rate: {}", request.get("taxRate"));
                }
            }

            if (request.containsKey("taxAmount") && request.get("taxAmount") != null) {
                try {
                    Object taxAmtObj = request.get("taxAmount");
                    lineItem.setTaxAmount(taxAmtObj instanceof Number ?
                            java.math.BigDecimal.valueOf(((Number) taxAmtObj).doubleValue()) :
                            new java.math.BigDecimal(taxAmtObj.toString()));
                } catch (Exception e) {
                    log.warn("Invalid tax amount: {}", request.get("taxAmount"));
                }
            }

            if (request.containsKey("productCode")) {
                lineItem.setProductCode((String) request.get("productCode"));
            }

            if (request.containsKey("unitOfMeasure")) {
                lineItem.setUnitOfMeasure((String) request.get("unitOfMeasure"));
            }

            lineItemRepository.save(lineItem);

            log.info("Successfully updated line item {} in invoice {}", lineItemId, invoiceId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Line item updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update line item {}: {}", lineItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to update line item: " + e.getMessage()
            ));
        }
    }

    // ============ INVOICE DELETION ENDPOINT ============

    /**
     * Delete invoice and all associated line items
     * Uses cascade delete configured on Invoice entity
     */
    @DeleteMapping("/{invoiceId}")
    public ResponseEntity<?> deleteInvoice(
            @PathVariable Long invoiceId,
            HttpServletRequest httpRequest) {
        try {
            String tenantId = (String) httpRequest.getAttribute("tenantId");
            String deletedBy = (String) httpRequest.getAttribute("userId");

            log.info("Deleting invoice {} in tenant {} by user {}", invoiceId, tenantId, deletedBy);

            // Verify invoice exists and belongs to tenant
            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found or access denied"));

            // Get line item count for logging
            int lineItemCount = invoice.getLineItems().size();
            String invoiceNumber = invoice.getInvoiceNumber();

            // Delete invoice (cascade will delete line items, metadata, etc.)
            invoiceRepository.deleteById(invoiceId);

            log.info("Successfully deleted invoice {} (number: {}) with {} line items for tenant {}", 
                    invoiceId, invoiceNumber, lineItemCount, tenantId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Invoice %s deleted successfully (%d line items removed)", 
                            invoiceNumber, lineItemCount)
            ));

        } catch (Exception e) {
            log.error("Failed to delete invoice {}: {}", invoiceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to delete invoice: " + e.getMessage()
            ));
        }
    }
}

