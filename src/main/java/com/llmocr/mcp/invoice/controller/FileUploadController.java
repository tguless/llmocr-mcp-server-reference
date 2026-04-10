package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.FileUpload;
import com.llmocr.mcp.invoice.repository.FileUploadRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for file upload tracking
 * Generates unique IDs for filenames BEFORE files are uploaded to S3
 */
@RestController
@RequestMapping("/api/file-uploads")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileUploadRepository fileUploadRepository;

    /**
     * Create a FileUpload record and get generated filename BEFORE uploading to S3
     * This ensures filenames are unique across all tenants
     * 
     * @param originalFilename The original filename from the user
     * @param uploadSource Source of the upload (WEB_UI, S3_AUTO, API)
     * @return Generated filename with unique ID
     */
    @PostMapping("/generate-filename")
    public ResponseEntity<Map<String, Object>> generateFilename(
            @RequestParam String originalFilename,
            @RequestParam(defaultValue = "WEB_UI") String uploadSource,
            @RequestAttribute("tenantId") String tenantId,
            @RequestAttribute("userId") String userId) {
        
        log.info("Generating unique filename for '{}' in tenant '{}' from source '{}'", 
                originalFilename, tenantId, uploadSource);

        // Create FileUpload record (gets auto-generated ID)
        // Use UUID placeholder to avoid unique constraint violation on generatedFilename
        String placeholder = "pending_" + java.util.UUID.randomUUID().toString();
        FileUpload fileUpload = FileUpload.builder()
                .tenantId(tenantId)
                .originalFilename(originalFilename)
                .generatedFilename(placeholder) // UUID placeholder, will update after save
                .uploadSource(uploadSource)
                .uploadedBy(userId)
                .processingStatus(FileUpload.ProcessingStatus.UPLOADED)
                .createdBy(userId)
                .build();
        
        FileUpload saved = fileUploadRepository.save(fileUpload);
        
        // Generate unique filename: upload_{ID}_{original_name}
        String fileExtension = "";
        String baseName = originalFilename;
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = originalFilename.substring(0, lastDot);
            fileExtension = originalFilename.substring(lastDot);
        }
        
        String generatedFilename = String.format("upload_%d_%s%s", 
                saved.getId(), baseName, fileExtension);
        
        // Update the record with generated filename
        saved.setGeneratedFilename(generatedFilename);
        fileUploadRepository.save(saved);
        
        log.info("Generated unique filename: {} (FileUpload ID: {})", generatedFilename, saved.getId());

        // Return response
        Map<String, Object> response = new HashMap<>();
        response.put("fileUploadId", saved.getId());
        response.put("originalFilename", originalFilename);
        response.put("generatedFilename", generatedFilename);
        response.put("tenantId", tenantId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get FileUpload record by ID
     */
    @GetMapping("/{fileUploadId}")
    public ResponseEntity<FileUpload> getFileUpload(
            @PathVariable Long fileUploadId,
            @RequestAttribute("tenantId") String tenantId) {
        
        FileUpload fileUpload = fileUploadRepository.findById(fileUploadId)
                .orElseThrow(() -> new IllegalArgumentException("FileUpload not found"));
        
        // Verify tenant access
        if (!fileUpload.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }
        
        return ResponseEntity.ok(fileUpload);
    }

    /**
     * Update FileUpload with S3 details after upload completes
     */
    @PutMapping("/{fileUploadId}/s3-details")
    public ResponseEntity<Map<String, Object>> updateS3Details(
            @PathVariable Long fileUploadId,
            @RequestParam String s3BucketName,
            @RequestParam String s3ObjectKey,
            @RequestParam(required = false) Long fileSizeBytes,
            @RequestAttribute("tenantId") String tenantId) {
        
        FileUpload fileUpload = fileUploadRepository.findById(fileUploadId)
                .orElseThrow(() -> new IllegalArgumentException("FileUpload not found"));
        
        // Verify tenant access
        if (!fileUpload.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }
        
        // Update S3 details
        fileUpload.setS3BucketName(s3BucketName);
        fileUpload.setS3ObjectKey(s3ObjectKey);
        if (fileSizeBytes != null) {
            fileUpload.setFileSizeBytes(fileSizeBytes);
        }
        fileUploadRepository.save(fileUpload);
        
        log.info("Updated S3 details for FileUpload {}: bucket={}, key={}", 
                fileUploadId, s3BucketName, s3ObjectKey);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("fileUploadId", fileUploadId);
        
        return ResponseEntity.ok(response);
    }
}

