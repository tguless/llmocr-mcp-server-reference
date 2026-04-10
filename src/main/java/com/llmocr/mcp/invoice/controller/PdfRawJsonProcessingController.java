package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.JsonSchema;
import com.llmocr.mcp.invoice.domain.PdfRawJsonProcessing;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.JsonSchemaRepository;
import com.llmocr.mcp.invoice.repository.PdfRawJsonProcessingRepository;
import com.llmocr.mcp.invoice.service.JsonSchemaValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for PDF Raw JSON Processing
 * 
 * Provides endpoints for:
 * - Listing raw JSON processing records
 * - Getting a specific record
 * - Updating/correcting JSON (with re-validation)
 * - Querying by job ID or PDF filename
 */
@RestController
@RequestMapping("/api/pdf-raw-json")
@RequiredArgsConstructor
@Slf4j
public class PdfRawJsonProcessingController {

    private final PdfRawJsonProcessingRepository processingRepository;
    private final JsonSchemaRepository jsonSchemaRepository;
    private final JsonSchemaValidationService validationService;

    /**
     * Get all raw JSON processing records for the current tenant
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProcessingRecords(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String sourceFilename,
            HttpServletRequest request) {
        
        // Get authenticated user from request (set by RestApiJwtAuthenticationFilter)
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
        
        log.info("Getting raw JSON processing records for tenant: {}", tenantId);
        
        List<PdfRawJsonProcessing> records;
        
        if (jobId != null) {
            log.info("Filtering by jobId: {}", jobId);
            records = processingRepository.findByTenantIdAndJobIdOrderByCreatedAtDesc(tenantId, jobId);
        } else if (sourceFilename != null) {
            log.info("Filtering by sourceFilename: {}", sourceFilename);
            records = processingRepository.findByTenantIdAndSourceFilenameOrderByCreatedAtDesc(
                    tenantId, sourceFilename);
        } else {
            records = processingRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", records.size());
        response.put("records", records);
        
        return ResponseEntity.ok(response);
    }
    /**
     * Get a specific processing record by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProcessingRecord(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        
        String tenantId = currentUser.getTenantId();
        log.info("Getting processing record ID {} for tenant: {}", id, tenantId);
        
        PdfRawJsonProcessing record = processingRepository.findById(id)
                .orElse(null);
        
        if (record == null || !record.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Record not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("record", record);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get all records for a specific job
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<Map<String, Object>> getRecordsByJob(
            @PathVariable String jobId,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        
        String tenantId = currentUser.getTenantId();
        log.info("Getting processing records for job {} and tenant: {}", jobId, tenantId);
        
        List<PdfRawJsonProcessing> records = processingRepository
                .findByTenantIdAndJobIdOrderByCreatedAtDesc(tenantId, jobId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobId", jobId);
        response.put("count", records.size());
        response.put("records", records);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update/correct a raw JSON record with re-validation
     * 
     * This is the key endpoint for the correction UI.
     * It validates the corrected JSON against the schema before saving.
     */
    @PutMapping("/{id}/correct")
    public ResponseEntity<Map<String, Object>> correctJson(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        
        String tenantId = currentUser.getTenantId();
        String userId = currentUser.getId().toString();
        
        log.info("Correcting JSON for record ID {} by user: {}", id, userId);
        
        // Find the record
        PdfRawJsonProcessing processingRecord = processingRepository.findById(id)
                .orElse(null);
        
        if (processingRecord == null || !processingRecord.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Record not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        String correctedJson = requestBody.get("correctedJson");
        
        if (correctedJson == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Missing required field: correctedJson");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Get the schema this record was validated against
        JsonSchema schema = processingRecord.getJsonSchema();
        
        if (schema == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No schema associated with this record. Cannot re-validate.");
            return ResponseEntity.badRequest().body(error);
        }
        
        log.info("Re-validating against schema: name={}, version={}", 
                schema.getSchemaName(), schema.getSchemaVersion());
        
        // Validate corrected JSON against schema
        JsonSchemaValidationService.ValidationResult result = 
                validationService.validate(correctedJson, schema);
        
        if (!result.isValid()) {
            log.error("Corrected JSON failed validation with {} error(s)", result.getErrors().size());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Corrected JSON failed schema validation");
            error.put("validationErrors", result.getErrors());
            
            return ResponseEntity.badRequest().body(error);
        }
        
        log.info("✅ Corrected JSON passed validation");
        
        // Update the record
        processingRecord.setRawJsonInput(correctedJson);
        processingRecord.setProcessingStatus(PdfRawJsonProcessing.ProcessingStatus.SUCCESS);
        processingRecord.setValidationErrors(null); // Clear any previous errors
        processingRecord.setErrorMessage(null);
        
        // Pretty print the corrected JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
            Object parsed = mapper.readValue(correctedJson, Object.class);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            processingRecord.setProcessedOutput(prettyJson);
        } catch (Exception e) {
            processingRecord.setProcessedOutput(correctedJson);
        }
        
        PdfRawJsonProcessing updated = processingRepository.save(processingRecord);
        
        log.info("✅ Updated processing record: id={}", updated.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "JSON corrected and validated successfully");
        response.put("record", updated);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validate JSON without saving (preview validation)
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, Object>> validateCorrection(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        
        String tenantId = currentUser.getTenantId();
        log.info("Validating correction for record ID {}", id);
        
        // Find the record
        PdfRawJsonProcessing processingRecord = processingRepository.findById(id)
                .orElse(null);
        
        if (processingRecord == null || !processingRecord.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Record not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        String jsonData = requestBody.get("jsonData");
        
        if (jsonData == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Missing required field: jsonData");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Get the schema
        JsonSchema schema = processingRecord.getJsonSchema();
        
        if (schema == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "No schema associated with this record");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Validate
        JsonSchemaValidationService.ValidationResult result = 
                validationService.validate(jsonData, schema);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("valid", result.isValid());
        response.put("schemaUsed", Map.of(
            "name", schema.getSchemaName(),
            "version", schema.getSchemaVersion(),
            "id", schema.getId()
        ));
        
        if (!result.isValid()) {
            response.put("errors", result.getErrors());
        }
        
        log.info("Validation result: valid={}, errors={}", result.isValid(), result.getErrors().size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get processing statistics for a tenant
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getProcessingStats(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        
        String tenantId = currentUser.getTenantId();
        log.info("Getting processing statistics for tenant: {}", tenantId);
        
        long totalRecords = processingRepository.countByTenantId(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalRecords", totalRecords);
        
        return ResponseEntity.ok(response);
    }
}


