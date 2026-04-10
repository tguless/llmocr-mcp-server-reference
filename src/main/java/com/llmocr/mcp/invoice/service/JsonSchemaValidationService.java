package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for validating JSON data against JSON Schema definitions
 * 
 * Uses the NetworkNT JSON Schema Validator library which supports
 * JSON Schema Draft 2020-12, Draft-07, and earlier versions.
 * 
 * See: https://github.com/networknt/json-schema-validator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validation result containing validation status and errors
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<ValidationError> getErrors() {
            return errors;
        }

        public String getErrorsAsJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(errors);
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    /**
     * Validation error detail
     */
    public static class ValidationError {
        private String path;
        private String message;
        private String type;

        public ValidationError(String path, String message, String type) {
            this.path = path;
            this.message = message;
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * Validate JSON string against a JSON schema
     * 
     * @param jsonData Raw JSON string to validate
     * @param schema JSON Schema entity containing the schema definition
     * @return ValidationResult with validation status and errors
     */
    public ValidationResult validate(String jsonData, JsonSchema schema) {
        try {
            // Parse JSON data
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            
            // Parse schema definition
            JsonNode schemaNode = objectMapper.readTree(schema.getSchemaDefinition());
            
            return validateJsonNode(jsonNode, schemaNode);
            
        } catch (Exception e) {
            log.error("Failed to parse JSON or schema: {}", e.getMessage());
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(
                "$", 
                "Invalid JSON format: " + e.getMessage(), 
                "parsing_error"
            ));
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Validate JSON string against a schema definition string
     * 
     * @param jsonData Raw JSON string to validate
     * @param schemaDefinition JSON Schema definition string
     * @return ValidationResult with validation status and errors
     */
    public ValidationResult validate(String jsonData, String schemaDefinition) {
        try {
            // Parse JSON data
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            
            // Parse schema definition
            JsonNode schemaNode = objectMapper.readTree(schemaDefinition);
            
            return validateJsonNode(jsonNode, schemaNode);
            
        } catch (Exception e) {
            log.error("Failed to parse JSON or schema: {}", e.getMessage());
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(
                "$", 
                "Invalid JSON format: " + e.getMessage(), 
                "parsing_error"
            ));
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Validate JsonNode against schema JsonNode
     */
    private ValidationResult validateJsonNode(JsonNode jsonNode, JsonNode schemaNode) {
        try {
            // Create schema validator
            // Using Draft 2020-12 as it's the latest
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            com.networknt.schema.JsonSchema jsonSchema = factory.getSchema(schemaNode);
            
            // Validate
            Set<ValidationMessage> validationMessages = jsonSchema.validate(jsonNode);
            
            if (validationMessages.isEmpty()) {
                log.debug("JSON validation successful");
                return new ValidationResult(true, new ArrayList<>());
            }
            
            // Convert validation messages to our error format
            List<ValidationError> errors = new ArrayList<>();
            for (ValidationMessage msg : validationMessages) {
                // Get the path - ValidationMessage in 1.5.x uses getInstanceLocation() or toString()
                String path = "$";
                try {
                    // Try to get the instance location (JSON Pointer path)
                    if (msg.getInstanceLocation() != null) {
                        path = msg.getInstanceLocation().toString();
                    }
                } catch (Exception e) {
                    // Fallback: use the message itself which typically includes the path
                    log.debug("Could not get instance location from ValidationMessage: {}", e.getMessage());
                }
                
                errors.add(new ValidationError(
                    path,
                    msg.getMessage(),
                    msg.getType()
                ));
            }
            
            log.warn("JSON validation failed with {} error(s)", errors.size());
            return new ValidationResult(false, errors);
            
        } catch (Exception e) {
            log.error("Schema validation error: {}", e.getMessage());
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(
                "$", 
                "Schema validation error: " + e.getMessage(), 
                "schema_error"
            ));
            return new ValidationResult(false, errors);
        }
    }
}

