package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.FileUpload;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.repository.FileUploadRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.service.S3PdfRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Admin UI PDF Operations
 * Provides endpoints for the MCP admin UI to retrieve PDF presigned URLs
 */
@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminInvoicePdfController {

    private final InvoiceRepository invoiceRepository;
    private final FileUploadRepository fileUploadRepository;
    private final S3PdfRetrievalService s3PdfService;

    /**
     * Get presigned URL for PDF viewing in admin UI
     */
    @GetMapping("/{invoiceId}/pdf/presigned-url")
    public ResponseEntity<?> getPdfPresignedUrl(
            @PathVariable Long invoiceId,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");

            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            SourceLocation primary = sourceFromInvoice(invoice);
            Optional<SourceLocation> fallback = sourceFromFileUpload(invoiceId, tenantId);
            if (primary == null && fallback.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "No valid source bucket/key found for this invoice"
                ));
            }

            String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            String url;
            try {
                if (primary == null) {
                    throw new IllegalStateException("Primary source not available");
                }
                log.info("Admin: Generating presigned URL for invoice {} from bucket {} key {}",
                        invoiceId, primary.bucketName(), primary.objectKey());
                url = s3PdfService.getPdfPresignedUrl(
                        tenantId,
                        primary.bucketName(),
                        primary.objectKey(),
                        bearerToken
                );
            } catch (Exception primaryError) {
                if (fallback.isEmpty() || sameLocation(primary, fallback.get())) {
                    throw primaryError;
                }
                SourceLocation fallbackLocation = fallback.get();
                log.warn("Admin: Primary presign failed for invoice {} ({}). Retrying with file_uploads source bucket {} key {}",
                        invoiceId, primaryError.getMessage(), fallbackLocation.bucketName(), fallbackLocation.objectKey());
                url = s3PdfService.getPdfPresignedUrl(
                        tenantId,
                        fallbackLocation.bucketName(),
                        fallbackLocation.objectKey(),
                        bearerToken
                );
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", url
            ));

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for admin: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to generate presigned URL: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Stream PDF content directly (bypasses presigned URL issues)
     * Accepts token as query parameter for iframe authentication
     */
    @GetMapping("/{invoiceId}/pdf/stream")
    public ResponseEntity<?> streamPdf(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) String token,
            HttpServletRequest request) {
        try {
            // Try to get tenant ID from request attribute first (from header auth)
            String tenantId = (String) request.getAttribute("tenantId");
            
            // If not in attribute, try to extract from token query parameter
            if (tenantId == null && token != null && !token.isEmpty()) {
                // Token will be validated by the filter, we just need to set it in header
                // Actually, for query param tokens, we need to manually validate
                // For now, let's just check if tenant is in request
                log.warn("Token provided as query param, but tenant not in request context");
            }
            
            if (tenantId == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "error", "Unauthorized - no valid authentication"
                ));
            }

            Invoice invoice = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            SourceLocation primary = sourceFromInvoice(invoice);
            Optional<SourceLocation> fallback = sourceFromFileUpload(invoiceId, tenantId);
            if (primary == null && fallback.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "No valid source bucket/key found for this invoice"
                ));
            }

            String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            byte[] pdfBytes;
            try {
                if (primary == null) {
                    throw new IllegalStateException("Primary source not available");
                }
                log.info("Admin: Streaming PDF for invoice {} from bucket {} key {}",
                        invoiceId, primary.bucketName(), primary.objectKey());
                pdfBytes = s3PdfService.retrievePdfFromS3(
                        tenantId,
                        primary.bucketName(),
                        primary.objectKey(),
                        bearerToken != null ? bearerToken : token
                );
            } catch (Exception primaryError) {
                if (fallback.isEmpty() || sameLocation(primary, fallback.get())) {
                    throw primaryError;
                }
                SourceLocation fallbackLocation = fallback.get();
                log.warn("Admin: Primary stream failed for invoice {} ({}). Retrying with file_uploads source bucket {} key {}",
                        invoiceId, primaryError.getMessage(), fallbackLocation.bucketName(), fallbackLocation.objectKey());
                pdfBytes = s3PdfService.retrievePdfFromS3(
                        tenantId,
                        fallbackLocation.bucketName(),
                        fallbackLocation.objectKey(),
                        bearerToken != null ? bearerToken : token
                );
            }

            log.info("Admin: Successfully fetched PDF, size: {} bytes", pdfBytes.length);

            // Return PDF with proper headers for inline display
            // Note: X-Frame-Options is configured globally in McpSecurityConfiguration
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Failed to stream PDF for admin: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to stream PDF: " + e.getMessage()
            ));
        }
    }

    private SourceLocation sourceFromInvoice(Invoice invoice) {
        if (invoice.getS3BucketName() == null || invoice.getS3BucketName().isBlank()) {
            return null;
        }
        String objectKey = invoice.getS3ObjectKey() != null ? invoice.getS3ObjectKey() : invoice.getSourceFileName();
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return new SourceLocation(invoice.getS3BucketName(), objectKey);
    }

    private Optional<SourceLocation> sourceFromFileUpload(Long invoiceId, String tenantId) {
        return fileUploadRepository.findByInvoiceId(invoiceId)
                .filter(upload -> tenantId.equals(upload.getTenantId()))
                .flatMap(this::toSourceLocation);
    }

    private Optional<SourceLocation> toSourceLocation(FileUpload upload) {
        if (upload.getS3BucketName() == null || upload.getS3BucketName().isBlank()) {
            return Optional.empty();
        }
        if (upload.getS3ObjectKey() == null || upload.getS3ObjectKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SourceLocation(upload.getS3BucketName(), upload.getS3ObjectKey()));
    }

    private boolean sameLocation(SourceLocation left, SourceLocation right) {
        if (left == null || right == null) {
            return false;
        }
        return left.bucketName().equalsIgnoreCase(right.bucketName())
                && left.objectKey().equals(right.objectKey());
    }

    private record SourceLocation(String bucketName, String objectKey) {}
}

