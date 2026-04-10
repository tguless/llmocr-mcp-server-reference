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
 * REST API Controller for PDF Retrieval from S3
 * Enables invoice PDF viewing from S3/MinIO buckets
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class PdfRetrievalController {

    private final InvoiceRepository invoiceRepository;
    private final FileUploadRepository fileUploadRepository;
    private final S3PdfRetrievalService s3PdfService;

    /**
     * Download PDF content directly
     */
    @GetMapping("/{invoiceId}/pdf/content")
    public ResponseEntity<?> getPdfContent(
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
            byte[] pdfBytes;
            try {
                if (primary == null) {
                    throw new IllegalStateException("Primary source not available");
                }
                log.info("Retrieving PDF for invoice {} from bucket {} key {}",
                        invoiceId, primary.bucketName(), primary.objectKey());
                pdfBytes = s3PdfService.retrievePdfFromS3(
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
                log.warn("Primary content retrieval failed for invoice {} ({}). Retrying with file_uploads source bucket {} key {}",
                        invoiceId, primaryError.getMessage(), fallbackLocation.bucketName(), fallbackLocation.objectKey());
                pdfBytes = s3PdfService.retrievePdfFromS3(
                        tenantId,
                        fallbackLocation.bucketName(),
                        fallbackLocation.objectKey(),
                        bearerToken
                );
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Failed to retrieve PDF content: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve PDF: " + e.getMessage()
            ));
        }
    }

    /**
     * Get presigned URL for PDF viewing
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
                log.info("Generating presigned URL for invoice {} from bucket {} key {}",
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
                log.warn("Primary presigned URL generation failed for invoice {} ({}). Retrying with file_uploads source bucket {} key {}",
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
            log.error("Failed to generate presigned URL: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to generate presigned URL: " + e.getMessage()
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
