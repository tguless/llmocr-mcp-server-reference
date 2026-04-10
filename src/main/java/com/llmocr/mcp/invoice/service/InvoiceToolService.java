package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.FileUpload;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.repository.FileUploadRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import com.llmocr.mcp.invoice.repository.VendorRepository;
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
import java.util.*;

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
    private final VendorRepository vendorRepository;
    private final S3BucketConfigurationRepository s3BucketConfigRepository;
    private final FileUploadRepository fileUploadRepository;
    private final InvoiceValidationService invoiceValidationService;
    private final McpAuditService mcpAuditService;
    private final ObjectMapper objectMapper;
    private final LlmOcrS3ConfigClient llmOcrS3ConfigClient;

    /**
     * Process and store a new invoice from extracted data
     * 
     * Uses Spring AI @Tool annotation for automatic MCP tool registration
     * with the stateless MCP server framework.
     * 
     * IMPORTANT: vendorId should be obtained from lookupVendor or createVendor tools first.
     * This ensures proper data integrity and allows different vendors to use the same invoice number.
     */
    @Tool(description = "Process and store a new invoice from extracted data. Returns the created invoice ID or error details.")
    @Transactional
    public String processInvoice(
            @ToolParam(description = "The ID of the vendor to whom the invoice belongs. Use lookupVendor or createVendor first.") Long vendorId,
            @ToolParam(description = "The unique invoice number for the invoice.") String invoiceNumber,
            @ToolParam(description = "The name of the vendor.") String vendorName,
            @ToolParam(description = "The address of the vendor.", required = false) String vendorAddress,
            @ToolParam(description = "The name of the customer.", required = false) String customerName,
            @ToolParam(description = "The invoice date in YYYY-MM-DD format.") String invoiceDate,
            @ToolParam(description = "The due date in YYYY-MM-DD format.", required = false) String dueDate,
            @ToolParam(description = "The total amount of the invoice, including currency symbol and parentheses for negative numbers (e.g., ($100.00) or (100.00)).") String totalAmount,
            @ToolParam(description = "The currency code (e.g., USD, EUR, GBP). Defaults to USD if not provided.", required = false) String currency,
            @ToolParam(description = "A description for the invoice.", required = false) String description,
            @ToolParam(description = "The start date of the service period in YYYY-MM-DD format.", required = false) String servicePeriodStartDate,
            @ToolParam(description = "The end date of the service period in YYYY-MM-DD format.", required = false) String servicePeriodEndDate,
            @ToolParam(description = "The EXACT source filename from the upload. Use the value from {filename} template variable - DO NOT modify or generate a new name. Example: 'St_Johns_Invoice_30780.pdf'", required = false) String sourceFileName,
            @ToolParam(description = "S3 bucket name from {s3_bucket_name} template variable. Use the EXACT value provided.", required = false) String s3BucketName,
            @ToolParam(description = "S3 object key from {s3_object_key} template variable. Use the EXACT value provided.", required = false) String s3ObjectKey) {
        
        long startTime = System.currentTimeMillis();
        
        // Get user context from stateless MCP server framework
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        // Validate authentication (handled by Spring AI MCP framework)
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
        }
        
        try {
            // CRITICAL: Override AI-provided values with actual values from request headers (guaranteed correct)
            // Backend injects these via X-Source-Filename, X-S3-Bucket, X-S3-Object-Key headers
            // This prevents the AI from inventing filenames or using wrong bucket names
            String actualSourceFileName = McpSecurityContext.getJobSourceFilename();
            String actualS3BucketName = McpSecurityContext.getJobS3Bucket();
            String actualS3ObjectKey = McpSecurityContext.getJobS3ObjectKey();
            
            // Fallback to AI-provided values if headers not present (manual tool calls)
            if (actualSourceFileName == null) {
                actualSourceFileName = sourceFileName;
                log.debug("Using AI-provided sourceFileName (no job context): {}", sourceFileName);
            } else if (sourceFileName != null && !actualSourceFileName.equals(sourceFileName)) {
                log.warn("AI tried to use sourceFileName '{}', overriding with actual from job: '{}'", 
                        sourceFileName, actualSourceFileName);
            }
            
            if (actualS3BucketName == null) {
                actualS3BucketName = s3BucketName;
                log.debug("Using AI-provided s3BucketName (no job context): {}", s3BucketName);
            } else if (s3BucketName != null && !actualS3BucketName.equals(s3BucketName)) {
                log.warn("AI tried to use s3BucketName '{}', overriding with actual from job: '{}'", 
                        s3BucketName, actualS3BucketName);
            }
            
            if (actualS3ObjectKey == null) {
                actualS3ObjectKey = s3ObjectKey;
                log.debug("Using AI-provided s3ObjectKey (no job context): {}", s3ObjectKey);
            } else if (s3ObjectKey != null && !actualS3ObjectKey.equals(s3ObjectKey)) {
                log.warn("AI tried to use s3ObjectKey '{}', overriding with actual from job: '{}'", 
                        s3ObjectKey, actualS3ObjectKey);
            }
            
            String jobId = McpSecurityContext.getJobId();
            log.info("Processing invoice for job {} with actual values: sourceFileName='{}', s3Bucket='{}', s3Key='{}'",
                    jobId, actualSourceFileName, actualS3BucketName, actualS3ObjectKey);
            
            log.info("Processing invoice {} for vendor {} in tenant {} by user {}", 
                    invoiceNumber, vendorId, tenantId, userId);

            // Validate required fields
            if (vendorId == null) {
                throw new IllegalArgumentException("Vendor ID is required. Use lookupVendor or createVendor first.");
            }
            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Invoice number is required");
            }
            if (vendorName == null || vendorName.trim().isEmpty()) {
                throw new IllegalArgumentException("Vendor name is required");
            }
            if (totalAmount == null || totalAmount.trim().isEmpty()) {
                throw new IllegalArgumentException("Total amount is required");
            }
            if (actualSourceFileName == null || actualSourceFileName.trim().isEmpty()) {
                throw new IllegalArgumentException("Source filename is required (from toolContext or parameter)");
            }
            if (actualS3BucketName == null || actualS3BucketName.trim().isEmpty()) {
                throw new IllegalArgumentException("S3 bucket name is required (from toolContext or parameter)");
            }

            // Verify vendor exists in the same tenant
            var vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, vendorId);
            if (vendorOpt.isEmpty()) {
                String message = String.format("Vendor ID %d not found for tenant %s", vendorId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // Check for duplicates (tenant + vendor + invoice number)
            if (invoiceRepository.existsByTenantIdAndVendorIdAndInvoiceNumber(tenantId, vendorId, invoiceNumber)) {
                String message = String.format("Invoice %s already exists for vendor %d in tenant %s", 
                        invoiceNumber, vendorId, tenantId);
                mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, message, startTime);
                throw new IllegalArgumentException(message);
            }

            // SECURITY: Verify S3 bucket belongs to this tenant
            // Check local config first, then fall back to LLMOCR API
            if (actualS3BucketName != null && !actualS3BucketName.trim().isEmpty()) {
                boolean bucketAuthorized = false;
                final String bucketNameForValidation = actualS3BucketName.trim();
                
                // 1. Check local S3 bucket configurations
                Optional<S3BucketConfiguration> localBucketConfig = s3BucketConfigRepository
                        .findByTenantIdAndBucketNameAndIsActiveTrue(tenantId, bucketNameForValidation);
                
                if (localBucketConfig.isPresent()) {
                    bucketAuthorized = true;
                    log.debug("S3 bucket validation passed via local config: {} belongs to tenant: {}", 
                            bucketNameForValidation, tenantId);
                } else {
                    // 2. Fall back to LLMOCR API (for buckets configured in main LLMOCR backend)
                    log.debug("Local bucket config not found, checking LLMOCR API for tenant: {}", tenantId);
                    try {
                        var llmOcrConfig = llmOcrS3ConfigClient.getTenantS3Config(tenantId, null);
                        if (llmOcrConfig.isPresent()) {
                            // Check if the requested bucket is in the tenant's LLMOCR config
                            boolean foundInLlmOcr = llmOcrConfig.get().buckets.stream()
                                    .anyMatch(b -> b.bucketName.equals(bucketNameForValidation) && b.isActive);
                            if (foundInLlmOcr) {
                                bucketAuthorized = true;
                                log.debug("S3 bucket validation passed via LLMOCR API: {} belongs to tenant: {}", 
                                        bucketNameForValidation, tenantId);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to check LLMOCR API for bucket authorization: {}", e.getMessage());
                    }
                }
                
                if (!bucketAuthorized) {
                    String message = String.format(
                            "S3 bucket '%s' is not configured for tenant '%s'. " +
                            "Invoices can only be assigned to buckets that belong to the tenant.", 
                            bucketNameForValidation, tenantId);
                    log.warn("SECURITY: Attempted to assign invoice to unauthorized bucket: {} for tenant: {}", 
                            bucketNameForValidation, tenantId);
                    mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, message, startTime);
                    throw new IllegalArgumentException(message);
                }
            }

            // Use actualS3ObjectKey if provided from toolContext, otherwise use actualSourceFileName
            if (actualS3ObjectKey == null || actualS3ObjectKey.trim().isEmpty()) {
                actualS3ObjectKey = actualSourceFileName;
                log.debug("S3 object key not provided, using sourceFileName: {}", actualS3ObjectKey);
            }

            // Parse job ID from String to Long
            Long jobIdLong = null;
            if (jobId != null && !jobId.trim().isEmpty()) {
                try {
                    jobIdLong = Long.parseLong(jobId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid job ID in context: {}", jobId);
                }
            }

            // Check if FileUpload record already exists for this file (created at upload time)
            FileUpload savedUpload = null;
            String generatedFilename = actualSourceFileName;

            // Deterministic lookup order:
            // 1) jobId (best unique key for this processing run)
            // 2) exact S3 bucket+key
            // 3) generated filename if source file already uses upload_ prefix
            // 4) tenant+original filename as last-resort fallback
            Optional<FileUpload> existingUpload = resolveExistingUpload(
                    tenantId, jobIdLong, actualS3BucketName, actualS3ObjectKey, actualSourceFileName);

            if (existingUpload.isPresent()) {
                // FileUpload was created at upload time - use its generated filename for internal tracking
                savedUpload = existingUpload.get();
                generatedFilename = savedUpload.getGeneratedFilename();
                savedUpload.setProcessingStatus(FileUpload.ProcessingStatus.PROCESSING);
                if (jobIdLong != null && !jobIdLong.equals(savedUpload.getJobId())) {
                    savedUpload.setJobId(jobIdLong);
                }
                savedUpload = fileUploadRepository.save(savedUpload);
                
                log.info("Using existing FileUpload record {} with generated filename: {}", 
                        savedUpload.getId(), generatedFilename);
                
                // NOTE: Do NOT modify S3 object key - file exists in S3 with its actual key
                log.debug("Keeping S3 object key as-is: {}", actualS3ObjectKey);
            } else {
                // FileUpload doesn't exist yet - create it now
                // This happens for files that bypassed the upload API (legacy flow)
                // Use UUID placeholder to avoid unique constraint violation on generatedFilename
                String placeholder = "pending_" + java.util.UUID.randomUUID().toString();
                FileUpload fileUpload = FileUpload.builder()
                        .tenantId(tenantId)
                        .originalFilename(actualSourceFileName)
                        .generatedFilename(placeholder) // UUID placeholder, will update after save
                        .s3BucketName(actualS3BucketName)
                        .s3ObjectKey(actualS3ObjectKey)
                        .uploadSource(jobIdLong != null ? "S3_AUTO" : "LEGACY")
                        .uploadedBy(userId)
                        .jobId(jobIdLong)
                        .processingStatus(FileUpload.ProcessingStatus.PROCESSING)
                        .createdBy(userId)
                        .build();
                
                savedUpload = fileUploadRepository.save(fileUpload);
                
                // Generate new filename with FileUpload ID: upload_{ID}_{original_name}
                String fileExtension = "";
                String baseName = actualSourceFileName;
                int lastDot = actualSourceFileName.lastIndexOf('.');
                if (lastDot > 0) {
                    baseName = actualSourceFileName.substring(0, lastDot);
                    fileExtension = actualSourceFileName.substring(lastDot);
                }
                
                String jobPrefix = jobIdLong != null ? ("job_" + jobIdLong + "_") : "";
                generatedFilename = String.format("upload_%s%d_%s%s",
                        jobPrefix, savedUpload.getId(), baseName, fileExtension);
                
                savedUpload.setGeneratedFilename(generatedFilename);
                savedUpload = fileUploadRepository.save(savedUpload);
                
                log.warn("Created FileUpload record {} retroactively (legacy flow) with generated filename: {}", 
                        savedUpload.getId(), generatedFilename);

                // NOTE: We do NOT modify the S3 object key - the file already exists in S3 with its original key
                // The generated filename is just for internal tracking, not for S3 path
                log.debug("Keeping original S3 object key: {} (not renaming to {})", actualS3ObjectKey, generatedFilename);
            }

            // Create invoice entity
            // IMPORTANT: Use actualS3ObjectKey (where file actually is in S3), not generatedFilename
            Invoice invoice = Invoice.builder()
                    .tenantId(tenantId)
                    .vendorId(vendorId)
                    .invoiceNumber(invoiceNumber.trim())
                    .vendorName(vendorName.trim())
                    .vendorAddress(vendorAddress)
                    .customerName(customerName)
                    .invoiceDate(parseDate(invoiceDate))
                    .dueDate(parseDate(dueDate))
                    .servicePeriodStartDate(parseDate(servicePeriodStartDate))
                    .servicePeriodEndDate(parseDate(servicePeriodEndDate))
                    .totalAmount(parseCurrencyAmount(totalAmount))
                    .currency(currency != null ? currency : "USD")
                    .description(description)
                    .status(Invoice.InvoiceStatus.PENDING)
                    .processingStatus(Invoice.ProcessingStatus.NEW)
                    .createdBy(userId)
                    .sourceFileName(actualSourceFileName)   // Original filename for display
                    .s3BucketName(actualS3BucketName)       // From toolContext (tenant bucket) not AI
                    .s3ObjectKey(actualS3ObjectKey)         // ACTUAL S3 key where file exists
                    .build();

            // Validate invoice
            List<String> validationErrors = invoiceValidationService.validateInvoice(invoice);
            if (!validationErrors.isEmpty()) {
                String errorMessage = "Validation failed: " + String.join(", ", validationErrors);
                mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, errorMessage, startTime);
                throw new IllegalArgumentException(errorMessage);
            }

            // Save invoice
            Invoice savedInvoice = invoiceRepository.save(invoice);
            
            // Link FileUpload to the created invoice
            savedUpload.setInvoiceId(savedInvoice.getId());
            savedUpload.setProcessingStatus(FileUpload.ProcessingStatus.COMPLETED);
            fileUploadRepository.save(savedUpload);
            
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", true, 
                    "Invoice processed successfully", startTime);

            log.info("Successfully processed invoice {} with ID {} for tenant {} (FileUpload: {})", 
                    invoiceNumber, savedInvoice.getId(), tenantId, savedUpload.getId());

            return String.format("SUCCESS: Invoice processed with ID %d (FileUpload ID: %d, Generated filename: %s)", 
                    savedInvoice.getId(), savedUpload.getId(), generatedFilename);

        } catch (Exception e) {
            log.error("Failed to process invoice {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "processInvoice", false, e.getMessage(), startTime);
            throw new RuntimeException("Failed to process invoice: " + e.getMessage(), e);
        }
    }

    /**
     * Check if an invoice exists by invoice number
     */
    @Tool(description = "Check if an invoice exists by invoice number. Returns true/false or error details.")
    @Transactional(readOnly = true)
    public String checkInvoiceExists(
            @ToolParam(description = "Invoice number to check (required)") String invoiceNumber) {
        // Get user context from stateless MCP server framework
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        // Validate authentication
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            log.warn("Unauthorized access attempt for checkInvoiceExists: {}", errorMessage);
            throw new SecurityException(errorMessage);
        }
        
        try {
            log.debug("Checking if invoice {} exists for tenant {} by user {}", invoiceNumber, tenantId, userId);

            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Invoice number is required");
            }

            boolean exists = invoiceRepository.existsByTenantIdAndInvoiceNumber(tenantId, invoiceNumber.trim());
            
            log.debug("Invoice existence check completed for {}: {}", invoiceNumber, exists);

            return exists ? "true" : "false";

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check invoice existence {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to check invoice existence: " + e.getMessage(), e);
        }
    }

    /**
     * Get invoice details by invoice number
     */
    @Tool(description = "Get invoice details by invoice number. Returns JSON invoice data or error details.")
    @Transactional(readOnly = true)
    public String getInvoiceByNumber(
            @ToolParam(description = "Invoice number to retrieve (required)") String invoiceNumber) {
        long startTime = System.currentTimeMillis();
        
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        if (!McpSecurityContext.isAuthenticated()) {
            String errorMessage = "Unauthorized: Valid Bearer token required";
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", false, errorMessage, startTime);
            throw new SecurityException(errorMessage);
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
            throw new RuntimeException("Failed to serialize invoice data", e);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get invoice {} for tenant {}: {}", invoiceNumber, tenantId, e.getMessage(), e);
            mcpAuditService.logOperation("TOOL_CALL", "getInvoiceByNumber", false, e.getMessage(), startTime);
            throw new RuntimeException("Failed to get invoice: " + e.getMessage(), e);
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
            throw new SecurityException(errorMessage);
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
            throw new RuntimeException("Failed to get invoice statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Parse currency amount string to BigDecimal, removing currency symbols
     * and handling negative numbers in parentheses notation (e.g., ($100.00) becomes -100.00)
     */
    private BigDecimal parseCurrencyAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        
        try {
            String trimmed = amountStr.trim();
            boolean isNegative = false;
            
            // Check for parentheses notation for negative numbers (e.g., ($100.00) or (100.00))
            if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                isNegative = true;
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
                log.debug("Detected negative amount in parentheses notation: '{}'", amountStr);
            }
            
            // Remove common currency symbols and whitespace
            String cleanAmount = trimmed
                    .replaceAll("[$€£¥₹]", "")  // Remove currency symbols
                    .replaceAll("[,\\s]", "")   // Remove commas and spaces
                    .trim();
            
            if (cleanAmount.isEmpty()) {
                throw new IllegalArgumentException("No numeric value found in amount: " + amountStr);
            }
            
            BigDecimal amount = new BigDecimal(cleanAmount);
            
            // Apply negative sign if parentheses notation was used
            if (isNegative) {
                amount = amount.negate();
            }
            
            log.debug("Parsed currency amount '{}' to '{}'", amountStr, amount);
            return amount;
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

    private Optional<FileUpload> resolveExistingUpload(
            String tenantId,
            Long jobIdLong,
            String actualS3BucketName,
            String actualS3ObjectKey,
            String actualSourceFileName) {

        if (jobIdLong != null) {
            List<FileUpload> byJob = fileUploadRepository.findByTenantIdAndJobIdOrderByCreatedAtDesc(tenantId, jobIdLong);
            if (!byJob.isEmpty()) {
                if (byJob.size() > 1) {
                    log.warn("Found {} FileUpload rows for tenant {} and jobId {}. Using newest record {}.",
                            byJob.size(), tenantId, jobIdLong, byJob.get(0).getId());
                }
                return Optional.of(byJob.get(0));
            }
        }

        if (actualS3BucketName != null && !actualS3BucketName.isBlank()
                && actualS3ObjectKey != null && !actualS3ObjectKey.isBlank()) {
            List<FileUpload> byS3 = fileUploadRepository.findByTenantIdAndS3BucketNameAndS3ObjectKeyOrderByCreatedAtDesc(
                    tenantId, actualS3BucketName, actualS3ObjectKey);
            if (!byS3.isEmpty()) {
                if (byS3.size() > 1) {
                    log.warn("Found {} FileUpload rows for tenant {} and s3://{}/{}. Using newest record {}.",
                            byS3.size(), tenantId, actualS3BucketName, actualS3ObjectKey, byS3.get(0).getId());
                }
                return Optional.of(byS3.get(0));
            }
        }

        if (actualSourceFileName != null && actualSourceFileName.startsWith("upload_")) {
            Optional<FileUpload> byGenerated = fileUploadRepository.findByGeneratedFilename(actualSourceFileName);
            if (byGenerated.isPresent()) {
                return byGenerated;
            }
        }

        if (actualSourceFileName != null && !actualSourceFileName.isBlank()) {
            List<FileUpload> byOriginal = fileUploadRepository.findByTenantIdAndOriginalFilenameOrderByCreatedAtDesc(
                    tenantId, actualSourceFileName);
            if (!byOriginal.isEmpty()) {
                if (byOriginal.size() > 1) {
                    log.warn("Found {} FileUpload rows for tenant {} and original filename '{}'. Using newest record {}.",
                            byOriginal.size(), tenantId, actualSourceFileName, byOriginal.get(0).getId());
                }
                return Optional.of(byOriginal.get(0));
            }
        }

        return Optional.empty();
    }
}
