package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.JsonSchema;
import com.llmocr.mcp.invoice.domain.PdfRawJsonProcessing;
import com.llmocr.mcp.invoice.repository.JsonSchemaRepository;
import com.llmocr.mcp.invoice.repository.PdfRawJsonProcessingRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Raw JSON Validation Tool Service with full schema validation
 * 
 * VALIDATION FLOW:
 * 1. Receives raw JSON string  
 * 2. Looks up JSON schema for tenant
 * 3. Validates JSON against schema
 * 4. ONLY saves to database if validation passes
 * 5. Returns validation errors if validation fails
 * 
 * Also stores the raw JSON processing paired with the PDF being processed
 * for audit and debugging purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RawJsonTestToolService {

    private final ObjectMapper objectMapper;
    private final PdfRawJsonProcessingRepository pdfRawJsonProcessingRepository;
    private final JsonSchemaRepository jsonSchemaRepository;
    private final JsonSchemaValidationService validationService;
    private final JsonAutoCorrector jsonAutoCorrector;

    /**
     * Validate and process raw JSON against a JSON schema
     */
    @Tool(description = "Validate and process raw JSON against a JSON schema. " +
                       "The JSON will be validated against the specified schema (default: 'test'). " +
                       "Returns validation errors if JSON doesn't match the schema. " +
                       "Only saves valid JSON to the database.")
    @Transactional
    public String llmOcrRawJsonTest(
            @ToolParam(description = "Raw JSON string to validate and process", required = true) 
            String rawJson) {
        
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        // DEBUG: Log exactly what rawJson contains (BEFORE any processing)
        log.info("🔍 RAW INPUT RECEIVED (first 500 chars): {}", 
                rawJson != null ? rawJson.substring(0, Math.min(500, rawJson.length())) : "NULL");
        log.info("🔍 RAW INPUT LENGTH: {}", rawJson != null ? rawJson.length() : 0);
        
        // CRITICAL: Check if rawJson parameter was provided
        if (rawJson == null || rawJson.trim().isEmpty()) {
            log.error("❌ llmOcrRawJsonTest called without rawJson parameter");
            throw new IllegalArgumentException(
                "ERROR: The 'rawJson' parameter is REQUIRED but was not provided. " +
                "You must pass the complete JSON as a string parameter. " +
                "Example: llmOcrRawJsonTest(rawJson: \"{...your JSON data...}\"). " +
                "Do NOT call this tool without the rawJson parameter."
            );
        }
        
        // SMART EXTRACTION: Handle LLM passing nested object vs string
        // LLMs often pass {"rawJson": {...}} instead of the escaped string
        String originalRawJson = rawJson;
        rawJson = extractActualJsonContent(rawJson);
        
        // DEBUG: Log after extraction
        log.info("🔍 AFTER EXTRACTION (first 500 chars): {}", rawJson.substring(0, Math.min(500, rawJson.length())));
        log.info("🔍 EXTRACTION CHANGED INPUT: {}", !originalRawJson.equals(rawJson));
        
        // Get schema name from X-JSON-Schema-Name header
        HttpServletRequest request = getCurrentRequest();
        String schemaName = request != null ? request.getHeader("X-JSON-Schema-Name") : null;
        
        // Default schema name if header not present
        if (schemaName == null || schemaName.trim().isEmpty()) {
            schemaName = "test";
        }
        
        log.info("=== llmOcrRawJsonTest Tool Invoked ===");
        log.info("Tenant ID: {}", tenantId);
        log.info("User ID: {}", userId);
        log.info("Schema Name: {}", schemaName);
        log.info("Raw JSON length: {} characters", rawJson != null ? rawJson.length() : 0);
        
        // Extract PDF context from headers
        String jobId = McpSecurityContext.getJobId();
        String sourceFilename = McpSecurityContext.getJobSourceFilename();
        String s3Bucket = McpSecurityContext.getJobS3Bucket();
        String s3ObjectKey = McpSecurityContext.getJobS3ObjectKey();
        
        log.info("PDF Context: jobId={}, filename={}, bucket={}, key={}", 
                jobId, sourceFilename, s3Bucket, s3ObjectKey);
        
        // Step 1: Check for X-JSON-Schema header (Base64 encoded) and decode it
        String pairedSchemaHeaderEncoded = request != null ? request.getHeader("X-JSON-Schema") : null;
        String pairedSchemaHeader = null;
        
        if (pairedSchemaHeaderEncoded != null && !pairedSchemaHeaderEncoded.isEmpty()) {
            try {
                // Base64 decode the schema
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(pairedSchemaHeaderEncoded);
                pairedSchemaHeader = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                log.info("✅ X-JSON-Schema header found and decoded (original size: {} bytes, decoded size: {} bytes)", 
                        pairedSchemaHeaderEncoded.length(), pairedSchemaHeader.length());
            } catch (IllegalArgumentException e) {
                log.error("❌ Failed to decode Base64-encoded X-JSON-Schema header: {}", e.getMessage());
                pairedSchemaHeader = null;
            }
        } else {
            log.warn("⚠️  X-JSON-Schema header not present");
        }
        
        String pairedSchemaNameHeader = schemaName; // Already retrieved from header
        
        if (pairedSchemaNameHeader != null && !pairedSchemaNameHeader.isEmpty()) {
            log.info("✅ X-JSON-Schema-Name header found: {}", pairedSchemaNameHeader);
        } else {
            log.warn("⚠️  X-JSON-Schema-Name header not present");
        }
        
        // Step 2: Determine which schema to use - header takes precedence over database
        JsonSchema schema = null;
        String schemaContent = null;
        
        // First, check if schema exists in database
        Optional<JsonSchema> schemaOpt = jsonSchemaRepository.findLatestActiveVersion(tenantId, schemaName);
        
        if (pairedSchemaHeader != null && !pairedSchemaHeader.isEmpty()) {
            // Use schema from header (sent by main backend)
            schemaContent = pairedSchemaHeader;
            log.info("📋 Using JSON Schema from X-JSON-Schema header (name: {}, size: {} bytes)", 
                    schemaName, schemaContent.length());
            
            // If schema doesn't exist in database, save it for future use
            if (schemaOpt.isEmpty()) {
                try {
                    JsonSchema newSchema = JsonSchema.builder()
                            .tenantId(tenantId)
                            .schemaName(schemaName)
                            .schemaVersion("1.0")
                            .schemaDefinition(schemaContent)
                            .isActive(true)
                            .description("Auto-imported from MCP tool header")
                            .createdBy(userId)
                            .createdAt(java.time.LocalDateTime.now())
                            .updatedAt(java.time.LocalDateTime.now())
                            .build();
                    schema = jsonSchemaRepository.save(newSchema);
                    log.info("💾 Saved new schema to database: name={}, version={}, id={}", 
                            schema.getSchemaName(), schema.getSchemaVersion(), schema.getId());
                } catch (Exception e) {
                    log.warn("⚠️  Failed to save schema to database (will continue with header schema): {}", e.getMessage());
                    // Continue without database schema - we have the header schema
                }
            } else {
                schema = schemaOpt.get();
                log.info("📋 Schema already exists in database: name={}, version={}, id={}", 
                        schema.getSchemaName(), schema.getSchemaVersion(), schema.getId());
            }
        } else {
            // No header schema - must use database
            if (schemaOpt.isEmpty()) {
                log.error("❌ JSON Schema not found: tenant={}, schema={}", tenantId, schemaName);
                String errorMessage = String.format(
                    "Schema not found: No active JSON schema found with name '%s' for tenant '%s'. " +
                    "Schema must be sent in X-JSON-Schema header or exist in database.", 
                    schemaName, tenantId);
                
                // Throw exception so the LLM knows the tool call FAILED
                throw new IllegalArgumentException(errorMessage);
            }
            
            schema = schemaOpt.get();
            schemaContent = schema.getSchemaDefinition();
            log.info("📋 Found JSON Schema from database: name={}, version={}, id={}", 
                    schema.getSchemaName(), schema.getSchemaVersion(), schema.getId());
        }
        
        // Step 3: Auto-correct common JSON issues from LLM output (if enabled)
        // Pass the schema so enum values can be corrected using schema definitions
        String correctedJson = jsonAutoCorrector.autoCorrect(rawJson, schemaContent);
        if (!correctedJson.equals(rawJson)) {
            log.info("📝 Auto-corrector modified the JSON (enabled: {}, schema-aware: true)", jsonAutoCorrector.isEnabled());
        }
        
        // Step 4: Validate JSON syntax
        try {
            objectMapper.readTree(correctedJson);
        } catch (Exception e) {
            log.error("❌ Invalid JSON syntax after auto-correction: {}", e.getMessage());
            String errorMessage = "Invalid JSON syntax even after auto-correction: " + e.getMessage() +
                "\n\nPlease fix the JSON syntax. Common issues: missing/extra commas, unescaped quotes, invalid characters.";
            
            // Throw exception so the LLM knows the tool call FAILED
            throw new IllegalArgumentException(errorMessage);
        }
        
        // Step 5: Validate JSON against schema (using corrected JSON)
        // DEBUG: Log exactly what we're about to validate
        log.info("🔍 VALIDATING JSON (first 500 chars): {}", correctedJson.substring(0, Math.min(500, correctedJson.length())));
        log.info("🔍 JSON TO VALIDATE LENGTH: {}", correctedJson.length());
        
        // Check if the JSON still has rawJson wrapper (which would be wrong)
        try {
            com.fasterxml.jackson.databind.JsonNode checkNode = objectMapper.readTree(correctedJson);
            if (checkNode.has("rawJson")) {
                log.error("❌ BUG DETECTED: JSON still has 'rawJson' wrapper! Extraction failed.");
                log.error("❌ JSON keys at root: {}", checkNode.fieldNames());
            } else {
                log.info("✅ JSON does NOT have 'rawJson' wrapper (good!)");
                log.info("✅ JSON keys at root: {}", java.util.stream.StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(checkNode.fieldNames(), java.util.Spliterator.ORDERED), false)
                    .collect(java.util.stream.Collectors.joining(", ")));
            }
        } catch (Exception e) {
            log.warn("Could not check JSON structure: {}", e.getMessage());
        }
        
        JsonSchemaValidationService.ValidationResult validationResult = 
                validationService.validate(correctedJson, schemaContent);
        
        if (!validationResult.isValid()) {
            log.error("❌ JSON Schema Validation FAILED with {} error(s)", 
                     validationResult.getErrors().size());
            
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append(String.format("JSON Schema validation FAILED against schema '%s' (version %s).\n\n", 
                             schema.getSchemaName(), schema.getSchemaVersion()));
            errorMessage.append("Validation errors:\n");
            
            for (JsonSchemaValidationService.ValidationError error : validationResult.getErrors()) {
                log.error("   - Path: {}, Message: {}", error.getPath(), error.getMessage());
                errorMessage.append(String.format("  - %s: %s\n", error.getPath(), error.getMessage()));
            }
            
            errorMessage.append("\nPlease fix the JSON to match the schema requirements and try again.");
            
            // Throw exception so the LLM knows the tool call FAILED
            throw new IllegalArgumentException(errorMessage.toString());
        }
        
        log.info("✅ JSON Schema Validation PASSED");
        
        // Step 5: Parse and pretty-print the CORRECTED JSON (not the original!)
        String prettyJson;
        try {
            Object parsed = objectMapper.readValue(correctedJson, Object.class);
            prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            prettyJson = correctedJson;
        }
        
        // Step 6: Save to database (validation passed)
        // Save the CORRECTED JSON, not the original rawJson
        PdfRawJsonProcessing savedRecord = savePdfRawJsonProcessing(
            tenantId, userId, jobId, sourceFilename, s3Bucket, s3ObjectKey,
            "llmOcrRawJsonTest", correctedJson, prettyJson, pairedSchemaHeader, pairedSchemaNameHeader,
            PdfRawJsonProcessing.ProcessingStatus.SUCCESS,
            null, null, schema
        );
        
        log.info("✅ Saved validated JSON to database: record_id={}", savedRecord.getId());
        
        // Step 7: Create success response
        String response = createSuccessResponse(prettyJson, schema, savedRecord.getId());
        
        return response;
    }
    
    /**
     * Create success response
     */
    private String createSuccessResponse(String prettyJson, JsonSchema schema, Long recordId) {
        try {
            return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("message", "Raw JSON validated and processed successfully");
                put("recordId", recordId);
                put("schemaValidated", new java.util.HashMap<String, Object>() {{
                    put("name", schema.getSchemaName());
                    put("version", schema.getSchemaVersion());
                    put("id", schema.getId());
                }});
                put("data", objectMapper.readTree(prettyJson));
            }});
        } catch (Exception e) {
            return "{\"success\": true, \"message\": \"Processed but failed to format response\"}";
        }
    }
    
    /**
     * Create validation error response
     */
    private String createValidationErrorResponse(
            String errorType, 
            String message, 
            JsonSchemaValidationService.ValidationResult validationResult) {
        
        try {
            return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("errorType", errorType);
                put("message", message);
                if (validationResult != null && !validationResult.getErrors().isEmpty()) {
                    put("validationErrors", validationResult.getErrors());
                }
            }});
        } catch (Exception e) {
            return String.format("{\"success\": false, \"errorType\": \"%s\", \"message\": \"%s\"}", 
                               errorType, message.replace("\"", "\\\""));
        }
    }
    
    /**
     * Get the current HTTP request from Spring's RequestContextHolder
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.debug("Could not get HTTP request from context: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Save PDF raw JSON processing record to database
     */
    private PdfRawJsonProcessing savePdfRawJsonProcessing(
            String tenantId,
            String userId,
            String jobId,
            String sourceFilename,
            String s3Bucket,
            String s3ObjectKey,
            String toolName,
            String rawJsonInput,
            String processedOutput,
            String pairedSchemaUsed,
            String pairedSchemaName,
            PdfRawJsonProcessing.ProcessingStatus status,
            String errorMessage,
            String validationErrors,
            JsonSchema schema) {
        
        try {
            LocalDateTime now = LocalDateTime.now();
            PdfRawJsonProcessing record = PdfRawJsonProcessing.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .jobId(jobId)
                    .sourceFilename(sourceFilename)
                    .s3Bucket(s3Bucket)
                    .s3ObjectKey(s3ObjectKey)
                    .toolName(toolName)
                    .rawJsonInput(rawJsonInput)
                    .processedOutput(processedOutput)
                    .pairedSchemaUsed(pairedSchemaUsed)
                    .pairedSchemaName(pairedSchemaName)
                    .jsonSchema(schema)  // Foreign key reference
                    .validationErrors(validationErrors)
                    .processingStatus(status)
                    .errorMessage(errorMessage)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            
            PdfRawJsonProcessing saved = pdfRawJsonProcessingRepository.save(record);
            
            log.info("✅ Saved PDF raw JSON processing record: id={}, status={}, schema_id={}", 
                    saved.getId(), status, schema != null ? schema.getId() : null);
            
            return saved;
            
        } catch (Exception e) {
            log.error("❌ Failed to save PDF raw JSON processing record: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save processing record", e);
        }
    }
    
    /**
     * Smart extraction of JSON content from various LLM output formats.
     * 
     * LLMs often don't follow instructions precisely. Instead of passing a JSON string,
     * they may pass:
     * - A nested object: {"rawJson": {"metadata": {...}}}
     * - Double-serialized: "{\"rawJson\": {\"metadata\": {...}}}"
     * - Wrapped in markdown: ```json {...} ```
     * - Java Map.toString() format: {key=value} (when Spring AI converts Map to String)
     * 
     * This method extracts the actual JSON content regardless of how the LLM formatted it.
     * This is the same trick IDE agents use - post-processing to repair common mistakes.
     * 
     * @param input The raw input from the LLM
     * @return The extracted JSON content ready for validation
     */
    private String extractActualJsonContent(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }
        
        String trimmed = input.trim();
        log.debug("🔍 Extracting JSON content from input (length: {})", trimmed.length());
        log.debug("🔍 First 200 chars: {}", trimmed.substring(0, Math.min(200, trimmed.length())));
        
        // CRITICAL FIX: Check if Spring AI passed a Map.toString() instead of valid JSON
        // Map.toString() produces: {key=value, ...} instead of {"key":"value", ...}
        // This happens when Spring AI converts a JsonNode/Map to String parameter
        if (isMapToStringFormat(trimmed)) {
            log.warn("⚠️ Input is Map.toString() format, not valid JSON. Converting...");
            log.warn("⚠️ First 300 chars: {}", trimmed.substring(0, Math.min(300, trimmed.length())));
            
            try {
                String converted = convertMapToStringToJson(trimmed);
                log.info("✅ Successfully converted Map.toString() to JSON (length: {} -> {})", 
                        trimmed.length(), converted.length());
                log.info("✅ Converted first 300 chars: {}", converted.substring(0, Math.min(300, converted.length())));
                return converted;
            } catch (Exception e) {
                log.error("❌ Failed to convert Map.toString() to JSON: {}", e.getMessage());
                // Fall through and let validation fail with helpful error
            }
        }
        
        try {
            // Step 1: Remove markdown code blocks if present
            if (trimmed.startsWith("```")) {
                int endFence = trimmed.lastIndexOf("```");
                if (endFence > 3) {
                    // Extract content between fences
                    int startContent = trimmed.indexOf('\n') + 1;
                    trimmed = trimmed.substring(startContent, endFence).trim();
                    log.info("📝 Removed markdown code fences from JSON");
                }
            }
            
            // Step 2: Try to parse as JSON to see what we have
            com.fasterxml.jackson.databind.JsonNode parsed = objectMapper.readTree(trimmed);
            
            // Step 3: Check if it's wrapped in {"rawJson": ...}
            if (parsed.isObject() && parsed.has("rawJson")) {
                com.fasterxml.jackson.databind.JsonNode innerNode = parsed.get("rawJson");
                
                if (innerNode.isTextual()) {
                    // rawJson is a string - return it directly
                    String innerJson = innerNode.asText();
                    log.info("📦 Extracted rawJson from wrapper (was string, length: {})", innerJson.length());
                    return innerJson;
                } else if (innerNode.isObject() || innerNode.isArray()) {
                    // rawJson is an object/array - serialize it back to string
                    String innerJson = objectMapper.writeValueAsString(innerNode);
                    log.info("📦 Extracted rawJson from wrapper (was object, converted to string, length: {})", innerJson.length());
                    return innerJson;
                }
            }
            
            // Step 4: Not wrapped - check if the root object has expected schema fields
            // If it has "metadata", "summary", "extraction" etc. it's probably the actual data
            if (parsed.isObject() && (parsed.has("metadata") || parsed.has("summary") || parsed.has("extraction"))) {
                log.info("✅ JSON appears to be correctly formatted (has expected schema fields)");
                return trimmed;
            }
            
            // Step 5: Return as-is and let validation handle it
            log.info("⚠️ JSON structure unrecognized, passing to validator as-is");
            return trimmed;
            
        } catch (Exception e) {
            // Not valid JSON - return as-is and let validation report the error
            log.warn("⚠️ Could not parse input as JSON during extraction: {}", e.getMessage());
            return trimmed;
        }
    }
    
    /**
     * Check if input is in Java Map.toString() format rather than JSON.
     * Map.toString() produces: {key=value, nested={inner=data}}
     * JSON produces: {"key":"value", "nested":{"inner":"data"}}
     */
    private boolean isMapToStringFormat(String input) {
        if (input == null || !input.startsWith("{")) {
            return false;
        }
        
        // Quick heuristic: Map.toString() has = for key-value separation
        // and typically doesn't have quotes around keys in the first 100 chars
        String sample = input.substring(0, Math.min(100, input.length()));
        
        // If it has unquoted = signs and no ":" near the start, it's likely Map.toString()
        boolean hasEquals = sample.contains("=");
        boolean hasQuotedKeys = sample.matches(".*\"[^\"]+\"\\s*:.*");
        boolean looksLikeMapToString = hasEquals && !hasQuotedKeys && 
                                        sample.matches("\\{[a-zA-Z_][a-zA-Z0-9_]*=.*");
        
        return looksLikeMapToString;
    }
    
    /**
     * Convert Java Map.toString() format to valid JSON.
     * 
     * Input:  {metadata={contractName=Power Purchase Agreement, fundName=N/A}, summary={total=5}}
     * Output: {"metadata":{"contractName":"Power Purchase Agreement","fundName":"N/A"},"summary":{"total":"5"}}
     * 
     * This is a recursive parser that handles:
     * - Nested objects: {key={inner=value}}
     * - Arrays: [elem1, elem2, {nested=obj}]
     * - String values with special characters
     * - Numeric values (kept as-is or quoted based on content)
     */
    private String convertMapToStringToJson(String input) {
        if (input == null) return null;
        
        StringBuilder result = new StringBuilder();
        convertMapToStringRecursive(input.trim(), 0, result);
        String jsonResult = result.toString();
        
        // Debug: Check if postCodObligations[10] has the required fields
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonResult);
            com.fasterxml.jackson.databind.JsonNode extraction = root.path("extraction");
            if (!extraction.isMissingNode()) {
                com.fasterxml.jackson.databind.JsonNode postCod = extraction.path("postCodObligations");
                if (postCod.isArray() && postCod.size() > 10) {
                    com.fasterxml.jackson.databind.JsonNode elem10 = postCod.get(10);
                    log.info("🔍 DEBUG: postCodObligations[10] after Map.toString() conversion:");
                    log.info("   ppaId: {}", elem10.path("ppaId").asText("MISSING"));
                    log.info("   task: {}", elem10.has("task") ? elem10.path("task").asText() : "❌ MISSING!");
                    log.info("   responsibleParty: {}", elem10.has("responsibleParty") ? elem10.path("responsibleParty").asText() : "❌ MISSING!");
                    log.info("   All keys present: {}", getFieldNames(elem10));
                }
            }
        } catch (Exception e) {
            log.debug("Could not debug postCodObligations[10]: {}", e.getMessage());
        }
        
        return jsonResult;
    }
    
    private String getFieldNames(com.fasterxml.jackson.databind.JsonNode node) {
        StringBuilder sb = new StringBuilder("[");
        node.fieldNames().forEachRemaining(name -> sb.append(name).append(", "));
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
    
    private int convertMapToStringRecursive(String input, int pos, StringBuilder out) {
        if (pos >= input.length()) return pos;
        
        char c = input.charAt(pos);
        
        if (c == '{') {
            // Start of object
            out.append('{');
            pos++;
            boolean first = true;
            
            while (pos < input.length()) {
                // Skip whitespace
                while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
                if (pos >= input.length()) break;
                
                c = input.charAt(pos);
                if (c == '}') {
                    out.append('}');
                    return pos + 1;
                }
                
                if (!first) {
                    // Expect comma between entries
                    if (c == ',') {
                        out.append(',');
                        pos++;
                        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
                    }
                }
                first = false;
                
                if (pos >= input.length()) break;
                
                // Parse key (until =)
                int keyStart = pos;
                while (pos < input.length() && input.charAt(pos) != '=' && input.charAt(pos) != '}') pos++;
                String key = input.substring(keyStart, pos).trim();
                
                if (pos < input.length() && input.charAt(pos) == '=') {
                    out.append('"').append(escapeJsonString(key)).append("\":");
                    pos++; // skip =
                    
                    // Skip whitespace after =
                    while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
                    
                    // Parse value
                    pos = parseMapValue(input, pos, out);
                }
            }
            out.append('}');
            return pos;
            
        } else if (c == '[') {
            // Start of array
            out.append('[');
            pos++;
            boolean first = true;
            
            while (pos < input.length()) {
                while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
                if (pos >= input.length()) break;
                
                c = input.charAt(pos);
                if (c == ']') {
                    out.append(']');
                    return pos + 1;
                }
                
                if (!first) {
                    if (c == ',') {
                        out.append(',');
                        pos++;
                        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
                    }
                }
                first = false;
                
                if (pos >= input.length()) break;
                
                // Parse array element - use array-specific parsing (no key=value lookup)
                pos = parseArrayElement(input, pos, out);
            }
            out.append(']');
            return pos;
        }
        
        return pos;
    }
    
    /**
     * Parse an array element from Map.toString() format.
     * Array elements are simpler than object values - they're separated by commas
     * and don't use key=value format, so we can split on commas directly.
     */
    private int parseArrayElement(String input, int pos, StringBuilder out) {
        if (pos >= input.length()) return pos;
        
        char c = input.charAt(pos);
        
        if (c == '{') {
            // Nested object in array
            return convertMapToStringRecursive(input, pos, out);
        } else if (c == '[') {
            // Nested array
            return convertMapToStringRecursive(input, pos, out);
        } else {
            // Scalar array element - find the end at next comma or ]
            int valueStart = pos;
            int braceDepth = 0;
            int bracketDepth = 0;
            
            while (pos < input.length()) {
                c = input.charAt(pos);
                if (c == '{') braceDepth++;
                else if (c == '}') {
                    if (braceDepth == 0) break;
                    braceDepth--;
                }
                else if (c == '[') bracketDepth++;
                else if (c == ']') {
                    if (bracketDepth == 0) break; // End of array
                    bracketDepth--;
                }
                else if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                    // In arrays, commas always separate elements (no key=value to check)
                    break;
                }
                pos++;
            }
            
            String value = input.substring(valueStart, pos).trim();
            
            // Determine if value should be quoted
            if (value.equals("null")) {
                out.append("null");
            } else if (value.equals("true") || value.equals("false")) {
                out.append(value);
            } else if (isNumeric(value)) {
                out.append(value);
            } else {
                // String value - quote it
                out.append('"').append(escapeJsonString(value)).append('"');
            }
            
            return pos;
        }
    }
    
    private int parseMapValue(String input, int pos, StringBuilder out) {
        if (pos >= input.length()) return pos;
        
        char c = input.charAt(pos);
        
        if (c == '{') {
            // Nested object
            return convertMapToStringRecursive(input, pos, out);
        } else if (c == '[') {
            // Array
            return convertMapToStringRecursive(input, pos, out);
        } else {
            // Scalar value - find the end
            // CRITICAL: Values can contain commas! e.g., "federal, state, and local permits"
            // We need to look for ", key=" or "}" to find the true end of a value
            int valueStart = pos;
            int braceDepth = 0;
            int bracketDepth = 0;
            
            while (pos < input.length()) {
                c = input.charAt(pos);
                if (c == '{') braceDepth++;
                else if (c == '}') {
                    if (braceDepth == 0) break;
                    braceDepth--;
                }
                else if (c == '[') bracketDepth++;
                else if (c == ']') {
                    if (bracketDepth == 0) break;
                    bracketDepth--;
                }
                else if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                    // Check if this comma separates key=value pairs or is part of the value
                    // Look ahead for "key=" pattern to determine if this is a field separator
                    if (isFieldSeparator(input, pos)) {
                        break; // This comma separates fields
                    }
                    // Otherwise, it's part of the value - continue
                }
                pos++;
            }
            
            String value = input.substring(valueStart, pos).trim();
            
            // Determine if value should be quoted
            if (value.equals("null")) {
                out.append("null");
            } else if (value.equals("true") || value.equals("false")) {
                out.append(value);
            } else if (isNumeric(value)) {
                out.append(value);
            } else {
                // String value - quote it
                out.append('"').append(escapeJsonString(value)).append('"');
            }
            
            return pos;
        }
    }
    
    /**
     * Check if a comma at the given position is a field separator (separating key=value pairs)
     * rather than a comma within a value (e.g., "federal, state, and local").
     * 
     * A comma is a field separator if it's followed by: whitespace*, identifier, =
     * e.g., ", nextKey=" or ",nextKey="
     */
    private boolean isFieldSeparator(String input, int commaPos) {
        int pos = commaPos + 1;
        
        // Skip whitespace after comma
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        
        if (pos >= input.length()) return true; // End of input after comma
        
        char c = input.charAt(pos);
        
        // If we hit a structure character, it's a separator (end of array element)
        if (c == '{' || c == '[' || c == ']' || c == '}') {
            return true;
        }
        
        // Look for identifier followed by =
        // Identifiers start with letter or underscore
        if (!Character.isLetter(c) && c != '_') {
            return false; // Not an identifier, so comma is part of value
        }
        
        // Scan the potential identifier
        int identStart = pos;
        while (pos < input.length()) {
            c = input.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        
        // Skip whitespace between identifier and =
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        
        // Check if followed by =
        if (pos < input.length() && input.charAt(pos) == '=') {
            // This looks like ", key=" - it's a field separator
            return true;
        }
        
        // No = found after identifier, so the comma is part of the value
        // e.g., "federal, state" where "state" is not followed by =
        return false;
    }
    
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private String escapeJsonString(String str) {
        if (str == null) return "";
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

