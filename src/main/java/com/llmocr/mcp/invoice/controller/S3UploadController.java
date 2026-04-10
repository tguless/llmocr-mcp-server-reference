package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.config.S3ClientFactory;
import com.llmocr.mcp.invoice.domain.FileUpload;
import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.repository.FileUploadRepository;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import com.llmocr.mcp.invoice.integration.InvoicePaperIqPromptTemplateNames;
import com.llmocr.mcp.invoice.service.LlmOcrS3ConfigClient;
import com.llmocr.mcp.invoice.service.S3PdfRetrievalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Controller for S3 file uploads (Invoice Server)
 * 
 * Allows users to upload files directly to configured S3 buckets.
 * Supports both local bucket configurations AND LLMOCR-configured buckets.
 */
@RestController
@RequestMapping("/api/s3-upload")
@RequiredArgsConstructor
@Slf4j
public class S3UploadController {

    private final S3BucketConfigurationRepository bucketConfigRepository;
    private final S3ClientFactory s3ClientFactory;
    private final FileUploadRepository fileUploadRepository;
    private final S3PdfRetrievalService s3PdfRetrievalService;
    private final LlmOcrS3ConfigClient llmOcrS3ConfigClient;

    /**
     * Get list of available S3 buckets for the user
     * Returns buckets from BOTH local configurations AND LLMOCR-configured buckets.
     */
    @GetMapping("/buckets")
    public ResponseEntity<Map<String, Object>> getAvailableBuckets(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        String bearerToken = (String) request.getAttribute("userToken");
        
        log.info("Getting available S3 buckets for tenant: {} (from both local and LLMOCR)", tenantId);

        try {
            // Get buckets from BOTH local config AND LLMOCR
            List<S3PdfRetrievalService.AvailableBucket> allBuckets = 
                    s3PdfRetrievalService.getAllAvailableBuckets(tenantId, bearerToken);
            
            // Convert to response format
            List<Map<String, Object>> buckets = allBuckets.stream()
                    .map(bucket -> {
                        Map<String, Object> b = new LinkedHashMap<>();
                        b.put("name", bucket.bucketName);
                        b.put("prefix", bucket.prefix);
                        b.put("description", bucket.description);
                        b.put("source", bucket.source);  // "local" or "llmocr"
                        b.put("isActive", bucket.isActive);
                        return b;
                    })
                    .collect(Collectors.toList());
            
            // Also include local configs with IDs (for upload functionality)
            List<S3BucketConfiguration> localConfigs = 
                    bucketConfigRepository.findByTenantIdAndIsActiveTrueOrderByBucketNameAsc(tenantId);
            
            List<Map<String, Object>> localBuckets = localConfigs.stream()
                    .map(config -> {
                        Map<String, Object> bucket = new LinkedHashMap<>();
                        bucket.put("id", config.getId());  // ID only for local configs
                        bucket.put("name", config.getBucketName());
                        bucket.put("endpoint", config.getEndpoint());
                        bucket.put("description", config.getDescription());
                        bucket.put("source", "local");
                        return bucket;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("all", buckets);  // Merged view from both sources
            data.put("local", localBuckets);  // Local configs with IDs (for upload)
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Buckets retrieved successfully from local and LLMOCR");
            response.put("data", data);
            
            log.info("Found {} total buckets for tenant {} ({} local, {} from LLMOCR)", 
                    buckets.size(), tenantId, localBuckets.size(), buckets.size() - localBuckets.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get available buckets for tenant: {}", tenantId, e);
            
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve buckets: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Upload files to a specific S3 bucket
     * Supports multiple file uploads at once
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFiles(
            HttpServletRequest request,
            @RequestParam("bucketConfigId") Long bucketConfigId,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam("files") List<MultipartFile> files) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String userId = (String) request.getAttribute("userId");
        
        log.info("Upload request from tenant {} to bucket config {} with {} files", 
                tenantId, bucketConfigId, files.size());

        if (files.isEmpty()) {
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "No files provided for upload");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Get bucket configuration
            S3BucketConfiguration bucketConfig = bucketConfigRepository.findById(bucketConfigId)
                    .orElseThrow(() -> new RuntimeException("Bucket configuration not found: " + bucketConfigId));
            
            // Verify tenant owns this bucket configuration
            if (!bucketConfig.getTenantId().equals(tenantId)) {
                log.warn("Tenant {} attempted to access bucket config {} owned by {}", 
                        tenantId, bucketConfigId, bucketConfig.getTenantId());
                
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Access denied to bucket configuration");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            // Create S3 client for this bucket
            S3Client s3Client = null;
            try {
                s3Client = s3ClientFactory.getS3Client(bucketConfig);
                
                // Ensure bucket exists before uploading
                s3ClientFactory.ensureBucketExists(bucketConfig, s3Client);
                
                List<Map<String, Object>> uploadedFiles = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                
                for (MultipartFile file : files) {
                    try {
                        String originalFilename = file.getOriginalFilename();
                        
                        // Step 1: Create FileUpload record FIRST to get unique ID
                        // Use UUID placeholder to avoid unique constraint violation on generatedFilename
                        String placeholder = "pending_" + java.util.UUID.randomUUID().toString();
                        FileUpload fileUpload = FileUpload.builder()
                                .tenantId(tenantId)
                                .originalFilename(originalFilename)
                                .generatedFilename(placeholder) // UUID placeholder, will update after save
                                .s3BucketName(bucketConfig.getBucketName())
                                .uploadSource("WEB_UI")
                                .uploadedBy(userId)
                                .processingStatus(FileUpload.ProcessingStatus.UPLOADED)
                                .createdBy(userId)
                                .fileSizeBytes(file.getSize())
                                .fileType(file.getContentType())
                                .build();
                        
                        FileUpload savedUpload = fileUploadRepository.save(fileUpload);
                        
                        // Step 2: Generate unique filename: upload_{ID}_{original_name}
                        String fileExtension = "";
                        String baseName = originalFilename;
                        int lastDot = originalFilename.lastIndexOf('.');
                        if (lastDot > 0) {
                            baseName = originalFilename.substring(0, lastDot);
                            fileExtension = originalFilename.substring(lastDot);
                        }
                        
                        String generatedFilename = String.format("upload_%d_%s%s", 
                                savedUpload.getId(), baseName, fileExtension);
                        
                        // Step 3: Update FileUpload with generated filename
                        savedUpload.setGeneratedFilename(generatedFilename);
                        savedUpload = fileUploadRepository.save(savedUpload);
                        
                        log.info("Created FileUpload record {} with generated filename: {}", 
                                savedUpload.getId(), generatedFilename);
                        
                        // Step 4: Generate S3 key using the generated filename
                        String s3Key = prefix != null && !prefix.isEmpty() 
                                ? prefix + "/" + generatedFilename 
                                : generatedFilename;
                        
                        // Step 5: Update FileUpload with S3 object key
                        savedUpload.setS3ObjectKey(s3Key);
                        savedUpload = fileUploadRepository.save(savedUpload);
                        
                        // Step 6: Add metadata
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("uploaded_by_tenant", tenantId);
                        if (userId != null) {
                            metadata.put("uploaded_by_user", userId);
                        }
                        metadata.put("upload_timestamp", String.valueOf(System.currentTimeMillis()));
                        metadata.put("original_filename", originalFilename);
                        metadata.put("file_upload_id", savedUpload.getId().toString());
                        
                        // Step 7: Upload file to S3 with GENERATED filename
                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(bucketConfig.getBucketName())
                                .key(s3Key)
                                .contentType(file.getContentType())
                                .contentLength(file.getSize())
                                .metadata(metadata)
                                .build();
                        
                        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
                        
                        Map<String, Object> uploadInfo = new LinkedHashMap<>();
                        uploadInfo.put("fileUploadId", savedUpload.getId());
                        uploadInfo.put("originalFilename", originalFilename);
                        uploadInfo.put("generatedFilename", generatedFilename);
                        uploadInfo.put("s3Key", s3Key);
                        uploadInfo.put("bucketName", bucketConfig.getBucketName());
                        uploadInfo.put("size", file.getSize());
                        uploadInfo.put("contentType", file.getContentType());
                        uploadInfo.put("success", true);
                        
                        uploadedFiles.add(uploadInfo);
                        log.info("Successfully uploaded {} as {} to bucket {} with key {}", 
                                originalFilename, generatedFilename, bucketConfig.getBucketName(), s3Key);
                        
                    } catch (Exception e) {
                        log.error("Failed to upload file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                        
                        // Provide user-friendly error messages for common S3 issues
                        String userFriendlyError = getUserFriendlyS3Error(e);
                        
                        Map<String, Object> errorInfo = new LinkedHashMap<>();
                        errorInfo.put("fileName", file.getOriginalFilename());
                        errorInfo.put("success", false);
                        errorInfo.put("error", userFriendlyError);
                        uploadedFiles.add(errorInfo);
                        errors.add(file.getOriginalFilename() + ": " + userFriendlyError);
                    }
                }
                
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("totalFiles", files.size());
                data.put("successfulUploads", uploadedFiles.stream().filter(f -> (Boolean) f.get("success")).count());
                data.put("failedUploads", errors.size());
                data.put("files", uploadedFiles);
                if (!errors.isEmpty()) {
                    data.put("errors", errors);
                }
                
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", data);
                
                if (errors.isEmpty()) {
                    response.put("success", true);
                    response.put("message", "All files uploaded successfully");
                    return ResponseEntity.ok(response);
                } else if (uploadedFiles.stream().anyMatch(f -> (Boolean) f.get("success"))) {
                    response.put("success", true);
                    response.put("message", "Some files uploaded successfully");
                    return ResponseEntity.ok(response);
                } else {
                    response.put("success", false);
                    response.put("message", "All file uploads failed");
                    return ResponseEntity.internalServerError().body(response);
                }
                
            } finally {
                if (s3Client != null) {
                    try {
                        s3Client.close();
                    } catch (Exception e) {
                        log.warn("Error closing S3 client: {}", e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during file upload for tenant {}: {}", tenantId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Upload failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Upload files to an LLMOCR-exposed bucket via LLMOCR's secure proxy.
     * 
     * SECURITY: MCP Invoice Server does NOT have direct S3 write access to LLMOCR buckets.
     * All uploads go through LLMOCR backend which:
     * 1. Validates tenant access to bucket+prefix
     * 2. Enforces prefix isolation
     * 3. Performs the actual S3 write using LLMOCR's IAM credentials
     * 
     * This prevents malicious MCP servers from bypassing tenant isolation.
     */
    @PostMapping("/upload-to-llmocr-bucket")
    public ResponseEntity<Map<String, Object>> uploadToLlmOcrBucket(
            HttpServletRequest request,
            @RequestParam(value = "bucketName", required = false) String bucketName,
            @RequestParam(value = "prefix", required = false) String requestedPrefix,
            @RequestParam(value = "promptTemplateName", required = false) String promptTemplateName,
            @RequestParam(value = "promptTemplateId", required = false) Long promptTemplateId,
            @RequestParam(value = "sourceExternalId", required = false) String sourceExternalId,
            @RequestParam(value = "sourceHeadersJson", required = false) String sourceHeadersJson,
            @RequestParam("files") List<MultipartFile> files) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String userId = (String) request.getAttribute("userId");
        String bearerToken = (String) request.getAttribute("userToken");
        
        final String effectiveBucket;
        final String effectivePrefix;
        final String templateNameArg;
        final Long templateIdArg;
        if (bucketName != null && !bucketName.isBlank()) {
            effectiveBucket = bucketName;
            effectivePrefix = requestedPrefix;
            templateNameArg = null;
            templateIdArg = null;
        } else {
            effectiveBucket = null;
            effectivePrefix = null;
            templateIdArg = promptTemplateId;
            templateNameArg = (promptTemplateName != null && !promptTemplateName.isBlank())
                    ? promptTemplateName
                    : InvoicePaperIqPromptTemplateNames.INVOICE_PROCESSING;
        }

        log.info("LLMOCR upload tenant {} bucket {} template {} ({} files)",
                tenantId, effectiveBucket, templateNameArg != null ? templateNameArg : "(n/a)", files.size());

        if (files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No files provided for upload"
            ));
        }

        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String originalFilename = file.getOriginalFilename();
                
                // Upload via LLMOCR proxy (LLMOCR validates and writes to S3)
                LlmOcrS3ConfigClient.UploadResult result = llmOcrS3ConfigClient.uploadFile(
                        tenantId,
                        effectiveBucket,
                        effectivePrefix,
                        originalFilename,
                        file.getBytes(),
                        file.getContentType(),
                        bearerToken,
                        sourceExternalId,
                        sourceHeadersJson,
                        templateNameArg,
                        templateIdArg
                );
                
                if (result.success) {
                    // Create FileUpload record for local tracking
                    // Use result.s3Key as generated filename (already unique from LLMOCR)
                    String generatedFn = result.s3Key.contains("/") 
                            ? result.s3Key.substring(result.s3Key.lastIndexOf('/') + 1) 
                            : result.s3Key;
                    String storedBucket = result.bucketName != null ? result.bucketName : effectiveBucket;
                    FileUpload fileUpload = FileUpload.builder()
                            .tenantId(tenantId)
                            .originalFilename(originalFilename)
                            .generatedFilename(generatedFn)
                            .s3BucketName(storedBucket)
                            .s3ObjectKey(result.s3Key)
                            .uploadSource("WEB_UI_LLMOCR_PROXY")
                            .uploadedBy(userId)
                            .processingStatus(FileUpload.ProcessingStatus.UPLOADED)
                            .createdBy(userId)
                            .fileSizeBytes(file.getSize())
                            .fileType(file.getContentType())
                            .build();
                    
                    FileUpload savedUpload = fileUploadRepository.save(fileUpload);
                    
                    Map<String, Object> uploadInfo = new LinkedHashMap<>();
                    uploadInfo.put("fileUploadId", savedUpload.getId());
                    uploadInfo.put("originalFilename", originalFilename);
                    uploadInfo.put("s3Key", result.s3Key);
                    uploadInfo.put("bucketName", storedBucket);
                    uploadInfo.put("size", file.getSize());
                    uploadInfo.put("contentType", file.getContentType());
                    uploadInfo.put("success", true);
                    
                    uploadedFiles.add(uploadInfo);
                    log.info("Successfully uploaded {} to LLMOCR bucket via proxy", originalFilename);
                    
                } else {
                    log.warn("LLMOCR proxy upload failed for {}: {}", originalFilename, result.error);
                    errors.add(originalFilename + ": " + result.error);
                    
                    Map<String, Object> uploadInfo = new LinkedHashMap<>();
                    uploadInfo.put("originalFilename", originalFilename);
                    uploadInfo.put("success", false);
                    uploadInfo.put("error", result.error);
                    uploadedFiles.add(uploadInfo);
                }
                
            } catch (Exception e) {
                log.error("Failed to upload file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            }
        }
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalFiles", files.size());
        data.put("successfulUploads", uploadedFiles.stream()
                .filter(f -> Boolean.TRUE.equals(f.get("success"))).count());
        data.put("failedUploads", errors.size());
        data.put("files", uploadedFiles);
        if (!errors.isEmpty()) {
            data.put("errors", errors);
        }
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        
        if (errors.isEmpty()) {
            response.put("success", true);
            response.put("message", "All files uploaded successfully via LLMOCR proxy");
            return ResponseEntity.ok(response);
        } else if (uploadedFiles.stream().anyMatch(f -> Boolean.TRUE.equals(f.get("success")))) {
            response.put("success", true);
            response.put("message", "Some files uploaded successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "All uploads failed");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Convert technical S3 error messages into user-friendly explanations
     */
    private String getUserFriendlyS3Error(Exception e) {
        String errorMessage = e.getMessage();
        
        if (errorMessage == null) {
            return "Unknown upload error occurred";
        }
        
        // Signature mismatch - credentials are wrong
        if (errorMessage.contains("signature") && errorMessage.contains("does not match")) {
            return "S3 credentials are incorrect. Please check your Access Key and Secret Key in Settings → S3 Configurations.";
        }
        
        // Access denied
        if (errorMessage.contains("Access Denied") || errorMessage.contains("403")) {
            return "Access denied. Please verify your S3 credentials have write permissions for this bucket.";
        }
        
        // Bucket doesn't exist
        if (errorMessage.contains("NoSuchBucket") || errorMessage.contains("bucket") && errorMessage.contains("does not exist")) {
            return "The S3 bucket does not exist. Please verify the bucket name in Settings.";
        }
        
        // Connection issues
        if (errorMessage.contains("Connection refused") || errorMessage.contains("Unable to connect")) {
            return "Cannot connect to S3 server. Please verify the endpoint URL in Settings.";
        }
        
        // Network timeout
        if (errorMessage.contains("timeout") || errorMessage.contains("timed out")) {
            return "Connection to S3 server timed out. Please check your network connection and S3 endpoint.";
        }
        
        // Default: return the original error but trimmed
        return errorMessage.length() > 200 ? errorMessage.substring(0, 200) + "..." : errorMessage;
    }
}

