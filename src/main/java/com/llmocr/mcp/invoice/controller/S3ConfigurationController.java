package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.config.S3ClientFactory;
import com.llmocr.mcp.invoice.config.S3Properties;
import com.llmocr.mcp.invoice.domain.S3BucketConfiguration;
import com.llmocr.mcp.invoice.repository.S3BucketConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller for S3 Bucket Configuration Management
 * Allows users to configure S3/MinIO bucket connections per tenant
 */
@RestController
@RequestMapping("/api/admin/s3-configurations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class S3ConfigurationController {

    private final S3BucketConfigurationRepository s3ConfigRepository;
    private final S3ClientFactory s3ClientFactory;
    private final S3Properties s3Properties;

    /**
     * Create new S3 bucket configuration
     */
    @PostMapping
    public ResponseEntity<?> createConfiguration(
            @RequestBody S3BucketConfiguration config,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            String userId = (String) request.getAttribute("userId");

            log.info("Creating S3 bucket configuration '{}' for tenant '{}'", 
                    config.getBucketName(), tenantId);

            // SECURITY: Check if bucket name is already claimed by ANY tenant (global uniqueness)
            List<S3BucketConfiguration> existingConfigs = s3ConfigRepository.findByBucketNameAndIsActiveTrue(config.getBucketName());
            if (!existingConfigs.isEmpty()) {
                // Check if any configuration belongs to a different tenant
                boolean claimedByOtherTenant = existingConfigs.stream()
                        .anyMatch(c -> !c.getTenantId().equals(tenantId));
                
                if (claimedByOtherTenant) {
                    log.warn("SECURITY: Tenant {} attempted to claim bucket '{}' already owned by another tenant", 
                            tenantId, config.getBucketName());
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                            "success", false,
                            "error", "Bucket '" + config.getBucketName() + 
                                    "' is already claimed by another tenant. Please choose a different bucket name."
                    ));
                }
            }

            config.setTenantId(tenantId);
            config.setCreatedBy(userId);
            config.setUpdatedBy(userId);

            S3BucketConfiguration saved = s3ConfigRepository.save(config);
            
            // Ensure the bucket exists after saving the configuration
            try {
                S3Client s3Client = s3ClientFactory.getS3Client(saved);
                s3ClientFactory.ensureBucketExists(saved, s3Client);
                s3Client.close();
                log.info("Bucket {} verified/created successfully", saved.getBucketName());
            } catch (Exception e) {
                log.warn("Failed to create bucket {}, but configuration was saved: {}", 
                        saved.getBucketName(), e.getMessage());
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "S3 bucket configuration created successfully",
                    "data", saved
            ));
        } catch (Exception e) {
            log.error("Failed to create S3 configuration: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to create configuration: " + e.getMessage()
            ));
        }
    }

    /**
     * List all S3 configurations for tenant
     */
    @GetMapping
    public ResponseEntity<?> listConfigurations(HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            List<S3BucketConfiguration> configs = s3ConfigRepository
                    .findByTenantIdAndIsActiveTrue(tenantId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", configs
            ));
        } catch (Exception e) {
            log.error("Failed to list S3 configurations: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve configurations"
            ));
        }
    }

    /**
     * Get specific S3 configuration
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getConfiguration(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");

            var config = s3ConfigRepository.findByIdAndTenantId(id, tenantId);
            if (config.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Configuration not found"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", config.get()
            ));
        } catch (Exception e) {
            log.error("Failed to get S3 configuration: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve configuration"
            ));
        }
    }

    /**
     * Update S3 configuration
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateConfiguration(
            @PathVariable Long id,
            @RequestBody S3BucketConfiguration config,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            String userId = (String) request.getAttribute("userId");

            var existing = s3ConfigRepository.findByIdAndTenantId(id, tenantId);
            if (existing.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Configuration not found"
                ));
            }

            S3BucketConfiguration toUpdate = existing.get();
            toUpdate.setBucketName(config.getBucketName());
            toUpdate.setEndpoint(config.getEndpoint());
            toUpdate.setAccessKeyId(config.getAccessKeyId());
            toUpdate.setSecretAccessKey(config.getSecretAccessKey());
            toUpdate.setRegion(config.getRegion());
            toUpdate.setDescription(config.getDescription());
            toUpdate.setUpdatedBy(userId);

            S3BucketConfiguration saved = s3ConfigRepository.save(toUpdate);
            
            // Ensure the bucket exists after updating the configuration
            try {
                S3Client s3Client = s3ClientFactory.getS3Client(saved);
                s3ClientFactory.ensureBucketExists(saved, s3Client);
                s3Client.close();
                log.info("Bucket {} verified/created successfully", saved.getBucketName());
            } catch (Exception e) {
                log.warn("Failed to create bucket {}, but configuration was saved: {}", 
                        saved.getBucketName(), e.getMessage());
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "S3 bucket configuration updated successfully",
                    "data", saved
            ));
        } catch (Exception e) {
            log.error("Failed to update S3 configuration: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to update configuration"
            ));
        }
    }

    /**
     * Delete (deactivate) S3 configuration
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConfiguration(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");

            var config = s3ConfigRepository.findByIdAndTenantId(id, tenantId);
            if (config.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Configuration not found"
                ));
            }

            S3BucketConfiguration toDelete = config.get();
            toDelete.setIsActive(false);
            s3ConfigRepository.save(toDelete);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "S3 bucket configuration deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to delete S3 configuration: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to delete configuration"
            ));
        }
    }

    /**
     * Test connection to S3 bucket
     */
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<?> testConnection(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");

            var config = s3ConfigRepository.findByIdAndTenantId(id, tenantId);
            if (config.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Configuration not found"
                ));
            }

            log.info("Testing connection to S3 bucket: {}", config.get().getBucketName());
            boolean success = s3ClientFactory.testConnection(config.get());

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Connection test successful"
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "error", "Failed to connect to S3 bucket"
                ));
            }
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Connection test failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get default S3 configuration values for new bucket configurations
     * 
     * @return Default endpoint, access key, and secret key configured in application.yml
     */
    @GetMapping("/default-endpoint")
    public ResponseEntity<?> getDefaultEndpoint() {
        log.debug("Fetching default S3 configuration values");
        return ResponseEntity.ok(Map.of(
                "defaultEndpoint", s3Properties.getDefaultEndpoint() != null 
                    ? s3Properties.getDefaultEndpoint() 
                    : "http://localhost:19001",
                "defaultAccessKey", s3Properties.getDefaultAccessKey() != null 
                    ? s3Properties.getDefaultAccessKey() 
                    : "minioadmin",
                "defaultSecretKey", s3Properties.getDefaultSecretKey() != null 
                    ? s3Properties.getDefaultSecretKey() 
                    : "minioadmin"
        ));
    }
}
