package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.config.S3ClientFactory;
import com.llmocr.mcp.invoice.config.S3Properties;
import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.OutputStream;
import java.util.Optional;

/**
 * Controller for streaming PDFs from S3/MinIO
 * 
 * Provides a generic endpoint for streaming any PDF from S3 storage
 * Used by Raw JSON Processing to display PDFs alongside JSON data
 */
@RestController
@RequestMapping("/api/s3-pdf")
@RequiredArgsConstructor
@Slf4j
public class S3PdfStreamController {

    private final S3ClientFactory s3ClientFactory;
    private final S3BucketConfigurationRepository bucketConfigRepository;
    private final S3Properties s3Properties;

    /**
     * Stream a PDF from S3/MinIO with tenant isolation
     * 
     * SECURITY: Only allows access to buckets configured for the authenticated user's tenant.
     * The bucket and key are validated against the tenant's S3 bucket configurations.
     * 
     * @param bucket S3 bucket name
     * @param key S3 object key
     * @param token JWT token for authentication (query parameter for iframe support)
     * @param request HTTP request (for authentication)
     * @param response HTTP response to stream to
     */
    @GetMapping("/stream")
    public void streamPdf(
            @RequestParam String bucket,
            @RequestParam String key,
            @RequestParam(required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        // SECURITY: Get tenant from authenticated user (not from query param!)
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            log.error("❌ Unauthorized access attempt to S3 PDF: bucket={}, key={}", bucket, key);
            try {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Authentication required\"}");
            } catch (Exception e) {
                log.error("Failed to write error response: {}", e.getMessage());
            }
            return;
        }
        
        String tenantId = currentUser.getTenantId();
        log.info("Streaming PDF from S3: bucket={}, key={}, tenant={}, user={}", 
                 bucket, key, tenantId, currentUser.getUsername());
        
        try {
            // SECURITY: Verify that this bucket is configured for the user's tenant
            // This prevents access to other tenants' buckets
            Optional<S3BucketConfiguration> configOpt = 
                bucketConfigRepository.findByTenantIdAndBucketNameAndIsActiveTrue(tenantId, bucket);
            
            if (configOpt.isEmpty()) {
                log.error("❌ SECURITY: Access denied to bucket '{}' for tenant '{}' - not configured or inactive", 
                         bucket, tenantId);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    String.format("{\"error\":\"Access denied\",\"message\":\"Bucket '%s' is not configured for your tenant\"}", 
                                 bucket)
                );
                return;
            }
            
            S3BucketConfiguration config = configOpt.get();
            
            // Create S3 client for this bucket
            S3Client s3Client = s3ClientFactory.getS3Client(config);
            
            // Get the PDF from S3
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
            
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            
            // Set response headers for PDF streaming
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader("Content-Disposition", "inline; filename=\"" + extractFilename(key) + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // Stream the PDF to the response
            try (OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = s3Object.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            
            s3Object.close();
            s3Client.close();
            
            log.info("✅ Successfully streamed PDF: {}", key);
            
        } catch (Exception e) {
            log.error("❌ Failed to stream PDF from S3: bucket={}, key={}, error={}", 
                     bucket, key, e.getMessage(), e);
            
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    String.format("{\"error\":\"Failed to stream PDF\",\"message\":\"%s\"}", 
                                 e.getMessage().replace("\"", "\\\""))
                );
            } catch (Exception writeError) {
                log.error("Failed to write error response: {}", writeError.getMessage());
            }
        }
    }
    
    /**
     * Extract filename from S3 object key
     */
    private String extractFilename(String key) {
        if (key == null || key.isEmpty()) {
            return "document.pdf";
        }
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }
}

