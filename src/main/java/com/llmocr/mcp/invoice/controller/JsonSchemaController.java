package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.JsonSchema;
import com.llmocr.mcp.invoice.repository.JsonSchemaRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import com.llmocr.mcp.invoice.service.JsonSchemaValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for JSON Schema Management
 * 
 * Provides endpoints for:
 * - Listing schemas for a tenant
 * - Creating new schemas
 * - Updating existing schemas
 * - Deactivating schemas
 * - Validating JSON against a schema
 */
@RestController
@RequestMapping("/api/json-schemas")
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaController {

    private final JsonSchemaRepository jsonSchemaRepository;
    private final JsonSchemaValidationService validationService;

    /**
     * Get all active schemas for the current tenant
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSchemas() {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        log.info("Getting all active schemas for tenant: {}", tenantId);
        
        List<JsonSchema> schemas = jsonSchemaRepository
                .findByTenantIdAndIsActiveTrueOrderBySchemaNameAscSchemaVersionDesc(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", schemas.size());
        response.put("schemas", schemas);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get all versions of a specific schema
     */
    @GetMapping("/name/{schemaName}")
    public ResponseEntity<Map<String, Object>> getSchemaVersions(@PathVariable String schemaName) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        log.info("Getting all versions of schema '{}' for tenant: {}", schemaName, tenantId);
        
        List<JsonSchema> schemas = jsonSchemaRepository
                .findByTenantIdAndSchemaNameOrderBySchemaVersionDesc(tenantId, schemaName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("schemaName", schemaName);
        response.put("count", schemas.size());
        response.put("versions", schemas);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific schema by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSchemaById(@PathVariable Long id) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        log.info("Getting schema ID {} for tenant: {}", id, tenantId);
        
        JsonSchema schema = jsonSchemaRepository.findById(id)
                .orElse(null);
        
        if (schema == null || !schema.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Schema not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("schema", schema);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new JSON schema
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSchema(@RequestBody Map<String, String> request) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        String schemaName = request.get("schemaName");
        String schemaVersion = request.get("schemaVersion");
        String description = request.get("description");
        String schemaDefinition = request.get("schemaDefinition");
        
        log.info("Creating schema '{}' version {} for tenant: {}", schemaName, schemaVersion, tenantId);
        
        // Validate required fields
        if (schemaName == null || schemaVersion == null || schemaDefinition == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Missing required fields: schemaName, schemaVersion, schemaDefinition");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Check if schema already exists
        if (jsonSchemaRepository.existsByTenantIdAndSchemaNameAndSchemaVersion(
                tenantId, schemaName, schemaVersion)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Schema with this name and version already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        
        // Validate schema definition is valid JSON
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(schemaDefinition);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Invalid JSON schema definition: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        
        // Create schema
        JsonSchema schema = JsonSchema.builder()
                .tenantId(tenantId)
                .schemaName(schemaName)
                .schemaVersion(schemaVersion)
                .description(description)
                .schemaDefinition(schemaDefinition)
                .isActive(true)
                .createdBy(userId)
                .build();
        
        JsonSchema saved = jsonSchemaRepository.save(schema);
        
        log.info("Created schema: id={}, name={}, version={}", saved.getId(), schemaName, schemaVersion);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Schema created successfully");
        response.put("schema", saved);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing schema
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSchema(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        String tenantId = McpSecurityContext.getCurrentTenantId();
        log.info("Updating schema ID {} for tenant: {}", id, tenantId);
        
        JsonSchema schema = jsonSchemaRepository.findById(id)
                .orElse(null);
        
        if (schema == null || !schema.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Schema not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        // Update fields if provided
        if (request.containsKey("description")) {
            schema.setDescription(request.get("description"));
        }
        
        if (request.containsKey("schemaDefinition")) {
            String schemaDefinition = request.get("schemaDefinition");
            
            // Validate it's valid JSON
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(schemaDefinition);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Invalid JSON schema definition: " + e.getMessage());
                return ResponseEntity.badRequest().body(error);
            }
            
            schema.setSchemaDefinition(schemaDefinition);
        }
        
        if (request.containsKey("isActive")) {
            schema.setIsActive(Boolean.parseBoolean(request.get("isActive")));
        }
        
        JsonSchema updated = jsonSchemaRepository.save(schema);
        
        log.info("Updated schema: id={}, name={}, version={}", updated.getId(), 
                updated.getSchemaName(), updated.getSchemaVersion());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Schema updated successfully");
        response.put("schema", updated);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a schema (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivateSchema(@PathVariable Long id) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        log.info("Deactivating schema ID {} for tenant: {}", id, tenantId);
        
        JsonSchema schema = jsonSchemaRepository.findById(id)
                .orElse(null);
        
        if (schema == null || !schema.getTenantId().equals(tenantId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Schema not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        schema.setIsActive(false);
        jsonSchemaRepository.save(schema);
        
        log.info("Deactivated schema: id={}, name={}, version={}", schema.getId(), 
                schema.getSchemaName(), schema.getSchemaVersion());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Schema deactivated successfully");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validate JSON against a schema
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateJson(@RequestBody Map<String, String> request) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        
        String jsonData = request.get("jsonData");
        String schemaName = request.get("schemaName");
        
        log.info("Validating JSON against schema '{}' for tenant: {}", schemaName, tenantId);
        
        if (jsonData == null || schemaName == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Missing required fields: jsonData, schemaName");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Look up schema
        JsonSchema schema = jsonSchemaRepository.findLatestActiveVersion(tenantId, schemaName)
                .orElse(null);
        
        if (schema == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Schema not found: " + schemaName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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
}








