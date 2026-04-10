package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.json.JsonSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AST-Based JSON Auto-Corrector for LLM Output
 * 
 * This implements the same pattern IDE agents use for code repair:
 * 1. Streaming AST parsing to detect syntax errors
 * 2. Pattern-based repair for common LLM mistakes
 * 3. Google JSON Sanitizer for complex repairs
 * 4. Validation after repair
 * 
 * Common LLM JSON mistakes this handles:
 * - Trailing commas
 * - Missing commas between elements
 * - Unclosed brackets/braces (truncation)
 * - Single quotes instead of double quotes
 * - Unescaped control characters in strings
 * - Unicode escape issues
 * - Markdown code fences wrapping JSON
 * - Python-style True/False/None
 */
@Service
@Slf4j
public class JsonAutoCorrector {

    @Value("${llm-ocr.json.auto-correct.enabled:true}")
    private boolean autoCorrectionEnabled;

    @Value("${llm-ocr.json.auto-correct.log-corrections:true}")
    private boolean logCorrections;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonFactory jsonFactory = new JsonFactory();
    
    // Cache for extracted enum definitions from schemas
    // Key: "fieldPath" (e.g., "obligationFrequency"), Value: Set of valid enum values
    private Map<String, List<String>> schemaEnumCache = new HashMap<>();

    /**
     * Attempt to auto-correct common JSON issues from LLM output.
     * Uses a multi-stage repair pipeline:
     * 
     * Stage 1: Quick fixes (markdown fences, Python literals)
     * Stage 2: AST-based bracket/brace balancing
     * Stage 3: Schema-aware enum value correction (if schema provided)
     * Stage 4: Google JSON Sanitizer for complex repairs
     * Stage 5: Validation
     * 
     * @param rawJson The potentially malformed JSON string
     * @return Corrected JSON string, or original if auto-correction is disabled or fails
     */
    public String autoCorrect(String rawJson) {
        return autoCorrect(rawJson, null);
    }
    
    /**
     * Attempt to auto-correct common JSON issues from LLM output with schema awareness.
     * 
     * @param rawJson The potentially malformed JSON string
     * @param schemaJson The JSON schema to use for enum extraction (can be null)
     * @return Corrected JSON string, or original if auto-correction is disabled or fails
     */
    public String autoCorrect(String rawJson, String schemaJson) {
        if (!autoCorrectionEnabled) {
            log.debug("JSON auto-correction is disabled");
            return rawJson;
        }

        if (rawJson == null || rawJson.trim().isEmpty()) {
            return rawJson;
        }

        String original = rawJson;
        String working = rawJson;
        boolean wasModified = false;

        try {
            log.info("🔧 Starting AST-based JSON repair pipeline...");
            
            // Stage 1: Quick fixes for common LLM output issues (always apply)
            String afterQuickFixes = applyQuickFixes(working);
            if (!afterQuickFixes.equals(working)) {
                working = afterQuickFixes;
                wasModified = true;
            }
            
            // Stage 2: If JSON is still invalid, try bracket balancing
            if (!isValidJson(working)) {
                String afterBrackets = balanceBrackets(working);
                if (!afterBrackets.equals(working)) {
                    working = afterBrackets;
                    wasModified = true;
                }
            }
            
            // Stage 3: If JSON is still invalid, use JSON Sanitizer to fix complex issues
            // This MUST happen BEFORE enum correction so we have valid JSON to parse
            if (!isValidJson(working)) {
                try {
                    String sanitized = JsonSanitizer.sanitize(working);
                    if (isValidJson(sanitized)) {
                        working = sanitized;
                        wasModified = true;
                        log.info("✅ JSON Sanitizer fixed syntax errors");
                        
                        // Debug: Check if JSON Sanitizer corrupted postCodObligations[10]
                        debugPostCodObligation10(working, "after JSON Sanitizer");
                    }
                } catch (Exception e) {
                    log.debug("JSON Sanitizer failed: {}", e.getMessage());
                }
            }
            
            // Stage 4: Schema-aware enum value correction
            // ALWAYS run this if schema is provided - it fixes semantic issues (wrong enum values)
            // JSON should be syntactically valid at this point
            if (schemaJson != null && !schemaJson.trim().isEmpty() && isValidJson(working)) {
                try {
                    Map<String, List<String>> enumDefs = extractEnumsFromSchema(schemaJson);
                    if (!enumDefs.isEmpty()) {
                        log.debug("🔧 Running schema-aware enum correction with {} enum definitions", enumDefs.size());
                        String afterEnumCorrection = correctEnumValues(working, enumDefs);
                        if (!afterEnumCorrection.equals(working)) {
                            working = afterEnumCorrection;
                            wasModified = true;
                            log.info("✅ Schema-aware enum correction applied");
                        }
                    }
                } catch (Exception e) {
                    log.warn("Schema-aware enum correction failed: {}", e.getMessage());
                }
            }
            
            // If JSON is valid, return it
            if (isValidJson(working)) {
                if (wasModified) {
                    logRepair("Auto-correction pipeline", original, working);
                }
                return working;
            }
            
            // Stage 5: Try sanitizer on original (in case our fixes made it worse)
            try {
                String sanitized = JsonSanitizer.sanitize(original);
                if (isValidJson(sanitized)) {
                    logRepair("JSON Sanitizer (original)", original, sanitized);
                    return sanitized;
                }
            } catch (Exception e) {
                log.debug("JSON Sanitizer on original failed: {}", e.getMessage());
            }
            
            // All repairs failed - return original for standard error handling
            log.warn("⚠️  All JSON repair attempts failed. Returning original for error reporting.");
            return original;

        } catch (Exception e) {
            log.error("❌ Exception during JSON auto-correction: {}", e.getMessage());
            return original;
        }
    }
    
    /**
     * Stage 1: Quick fixes for common LLM output patterns
     */
    private String applyQuickFixes(String json) {
        String result = json.trim();
        
        // Fix 1: Remove markdown code fences
        if (result.startsWith("```")) {
            // Find opening fence end
            int firstNewline = result.indexOf('\n');
            if (firstNewline > 0) {
                result = result.substring(firstNewline + 1);
            }
            // Remove closing fence
            if (result.endsWith("```")) {
                result = result.substring(0, result.length() - 3);
            } else {
                // Find last ``` that might be on its own line
                int lastFence = result.lastIndexOf("```");
                if (lastFence > 0) {
                    result = result.substring(0, lastFence);
                }
            }
            result = result.trim();
            log.debug("Removed markdown code fences");
        }
        
        // Fix 2: Python-style booleans and None
        result = result.replaceAll("(?<=[\\[{,:\\s])True(?=[\\]},\\s])", "true");
        result = result.replaceAll("(?<=[\\[{,:\\s])False(?=[\\]},\\s])", "false");
        result = result.replaceAll("(?<=[\\[{,:\\s])None(?=[\\]},\\s])", "null");
        
        // Fix 3: Trailing commas before closing brackets
        result = result.replaceAll(",\\s*}", "}");
        result = result.replaceAll(",\\s*]", "]");
        
        // Fix 4: Single quotes to double quotes (careful with nested quotes)
        // Only do this if there are no double quotes (simple case)
        if (!result.contains("\"") && result.contains("'")) {
            result = result.replace("'", "\"");
            log.debug("Converted single quotes to double quotes");
        }
        
        // Fix 5: Unquoted keys (simple pattern - won't catch all cases)
        // Pattern: { key: vs { "key":
        result = fixUnquotedKeys(result);
        
        return result;
    }
    
    /**
     * Fix unquoted keys in JSON
     */
    private String fixUnquotedKeys(String json) {
        // Pattern to match unquoted keys: { word: or , word:
        // But not if already quoted
        Pattern pattern = Pattern.compile("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)\\s*:");
        Matcher matcher = pattern.matcher(json);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String key = matcher.group(2);
            // Check if key is already quoted (look back for quote)
            int pos = matcher.start();
            boolean alreadyQuoted = pos > 0 && json.charAt(pos - 1) == '"';
            
            if (!alreadyQuoted) {
                matcher.appendReplacement(sb, prefix + "\"" + key + "\":");
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Stage 2: AST-based bracket balancing
     * Handles truncated JSON from LLMs hitting token limits
     */
    private String balanceBrackets(String json) {
        // Count open/close brackets
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        char prevChar = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            // Handle string state
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }
            
            if (!inString) {
                switch (c) {
                    case '{': openBraces++; break;
                    case '}': openBraces--; break;
                    case '[': openBrackets++; break;
                    case ']': openBrackets--; break;
                }
            }
            
            prevChar = c;
        }
        
        // If unbalanced, add closing brackets
        StringBuilder result = new StringBuilder(json);
        
        // Handle unclosed string
        if (inString) {
            result.append("\"");
            log.debug("Closed unclosed string");
        }
        
        // Add missing closing brackets
        while (openBrackets > 0) {
            result.append("]");
            openBrackets--;
        }
        while (openBraces > 0) {
            result.append("}");
            openBraces--;
        }
        
        if (result.length() != json.length()) {
            log.debug("Added {} closing characters to balance brackets", result.length() - json.length());
        }
        
        return result.toString();
    }
    
    /**
     * Extract all enum definitions from a JSON schema.
     * Walks the schema tree and finds all "enum" arrays.
     * 
     * @param schemaJson The JSON schema string
     * @return Map of field paths to their valid enum values
     */
    private Map<String, List<String>> extractEnumsFromSchema(String schemaJson) {
        Map<String, List<String>> enumDefs = new HashMap<>();
        
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            extractEnumsRecursive(schema, "", enumDefs);
            
            // Also check $defs for reusable definitions
            JsonNode defs = schema.get("$defs");
            if (defs == null) defs = schema.get("definitions");
            if (defs != null && defs.isObject()) {
                defs.fields().forEachRemaining(entry -> {
                    extractEnumsRecursive(entry.getValue(), "$defs." + entry.getKey(), enumDefs);
                });
            }
            
            log.debug("Extracted {} enum definitions from schema", enumDefs.size());
            for (Map.Entry<String, List<String>> entry : enumDefs.entrySet()) {
                log.debug("  Enum '{}': {}", entry.getKey(), entry.getValue());
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract enums from schema: {}", e.getMessage());
        }
        
        return enumDefs;
    }
    
    /**
     * Recursively walk schema to find enum definitions
     */
    private void extractEnumsRecursive(JsonNode node, String path, Map<String, List<String>> enumDefs) {
        if (node == null) return;
        
        // Check if this node has an enum
        JsonNode enumNode = node.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode val : enumNode) {
                if (val.isTextual()) {
                    values.add(val.asText());
                }
            }
            if (!values.isEmpty()) {
                // Extract the field name from the path (last segment)
                String fieldName = path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path;
                if (!fieldName.isEmpty()) {
                    enumDefs.put(fieldName, values);
                }
            }
        }
        
        // Check properties
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                extractEnumsRecursive(entry.getValue(), childPath, enumDefs);
            });
        }
        
        // Check items (for arrays)
        JsonNode items = node.get("items");
        if (items != null) {
            if (items.isObject()) {
                extractEnumsRecursive(items, path + "[]", enumDefs);
            }
        }
        
        // Check $ref
        JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual()) {
            // $ref typically looks like "#/$defs/obligation"
            // We handle $defs separately in the main method
        }
    }
    
    /**
     * Correct enum values in JSON using schema-extracted enum definitions.
     * Uses fuzzy matching to find the best valid enum value.
     */
    private String correctEnumValues(String json, Map<String, List<String>> enumDefs) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null) return json;
            
            boolean modified = correctEnumValuesRecursive(root, enumDefs);
            
            if (modified) {
                log.info("📝 Corrected enum values in JSON using schema definitions");
                return objectMapper.writeValueAsString(root);
            }
            
            return json;
        } catch (Exception e) {
            log.debug("Enum correction failed: {}", e.getMessage());
            return json;
        }
    }
    
    /**
     * Recursively walk JSON and correct enum values
     */
    private boolean correctEnumValuesRecursive(JsonNode node, Map<String, List<String>> enumDefs) {
        if (node == null) return false;
        
        boolean modified = false;
        
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            
            // Check each field
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            
            for (String fieldName : fieldNames) {
                JsonNode child = node.get(fieldName);
                
                // If this field has an enum definition, check/correct the value
                if (child != null && child.isTextual() && enumDefs.containsKey(fieldName)) {
                    List<String> validValues = enumDefs.get(fieldName);
                    String currentValue = child.asText();
                    
                    if (!validValues.contains(currentValue)) {
                        String corrected = findBestEnumMatch(currentValue, validValues);
                        if (!currentValue.equals(corrected)) {
                            obj.put(fieldName, corrected);
                            log.debug("Corrected enum '{}': '{}' -> '{}'", fieldName, currentValue, corrected);
                            modified = true;
                        }
                    }
                }
                
                // Recurse into child
                if (child != null) {
                    modified |= correctEnumValuesRecursive(child, enumDefs);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                modified |= correctEnumValuesRecursive(item, enumDefs);
            }
        }
        
        return modified;
    }
    
    /**
     * Find the best matching enum value using fuzzy matching.
     * 
     * Matching strategies (in order):
     * 1. Case-insensitive exact match
     * 2. Levenshtein distance (edit distance)
     * 3. Substring/contains match
     * 4. First word match (for "Daily (if delayed)" -> "Daily")
     * 5. Default to "Other" if available, else first value
     */
    private String findBestEnumMatch(String invalidValue, List<String> validValues) {
        if (invalidValue == null || validValues == null || validValues.isEmpty()) {
            return validValues != null && !validValues.isEmpty() ? validValues.get(0) : null;
        }
        
        String lower = invalidValue.toLowerCase().trim();
        
        // Strategy 1: Case-insensitive exact match
        for (String valid : validValues) {
            if (valid.equalsIgnoreCase(invalidValue)) {
                return valid;
            }
        }
        
        // Strategy 2: Extract first word/part (handles "Daily (if delayed)", "Annually + as needed")
        String firstPart = lower;
        if (lower.contains("(")) {
            firstPart = lower.substring(0, lower.indexOf("(")).trim();
        }
        if (firstPart.contains("+")) {
            firstPart = firstPart.substring(0, firstPart.indexOf("+")).trim();
        }
        if (firstPart.contains(" and ")) {
            firstPart = firstPart.substring(0, firstPart.indexOf(" and ")).trim();
        }
        
        for (String valid : validValues) {
            if (valid.equalsIgnoreCase(firstPart)) {
                return valid;
            }
        }
        
        // Strategy 3: Prefix/suffix match (e.g., "Annual" -> "Annually")
        // This is better than generic substring because "Annual" should match "Annually" not "Semi-annually"
        for (String valid : validValues) {
            String validLower = valid.toLowerCase();
            // Check if invalid is prefix of valid or vice versa
            if (validLower.startsWith(lower) || lower.startsWith(validLower)) {
                return valid;
            }
        }
        
        // Strategy 4: General substring match (fallback)
        for (String valid : validValues) {
            String validLower = valid.toLowerCase();
            if (validLower.contains(lower) || lower.contains(validLower)) {
                return valid;
            }
        }
        
        // Strategy 5: Levenshtein distance - find closest match
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : validValues) {
            int dist = levenshteinDistance(lower, valid.toLowerCase());
            if (dist < minDistance) {
                minDistance = dist;
                closest = valid;
            }
        }
        
        // Only use Levenshtein if reasonably close (within 50% of string length)
        if (closest != null && minDistance <= Math.max(lower.length(), closest.length()) / 2) {
            return closest;
        }
        
        // Strategy 6: Default to "Other" if available
        for (String valid : validValues) {
            if (valid.equalsIgnoreCase("Other")) {
                return valid;
            }
        }
        
        // Last resort: return first valid value
        return validValues.get(0);
    }
    
    /**
     * Calculate Levenshtein (edit) distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Check if JSON is valid using streaming parser
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Debug helper to check postCodObligations[10] at various stages
     */
    private void debugPostCodObligation10(String json, String stage) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode extraction = root.path("extraction");
            if (!extraction.isMissingNode()) {
                JsonNode postCod = extraction.path("postCodObligations");
                if (postCod.isArray() && postCod.size() > 10) {
                    JsonNode elem10 = postCod.get(10);
                    boolean hasTask = elem10.has("task");
                    boolean hasResponsibleParty = elem10.has("responsibleParty");
                    
                    if (!hasTask || !hasResponsibleParty) {
                        log.error("🚨 DEBUG {}: postCodObligations[10] MISSING FIELDS!", stage);
                        log.error("   ppaId: {}", elem10.path("ppaId").asText("MISSING"));
                        log.error("   task: {}", hasTask ? "present" : "❌ MISSING!");
                        log.error("   responsibleParty: {}", hasResponsibleParty ? "present" : "❌ MISSING!");
                        
                        // Log all keys present
                        StringBuilder keys = new StringBuilder("[");
                        elem10.fieldNames().forEachRemaining(name -> keys.append(name).append(", "));
                        if (keys.length() > 1) keys.setLength(keys.length() - 2);
                        keys.append("]");
                        log.error("   Keys present: {}", keys);
                    } else {
                        log.info("✅ DEBUG {}: postCodObligations[10] has all required fields", stage);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not debug postCodObligations[10] {}: {}", stage, e.getMessage());
        }
    }
    
    /**
     * Log repair details
     */
    private void logRepair(String stage, String original, String repaired) {
        if (logCorrections) {
            log.info("✅ JSON repaired by: {}", stage);
            log.debug("Original length: {}, Repaired length: {}", original.length(), repaired.length());
            
            // Log if significant changes
            int diff = Math.abs(original.length() - repaired.length());
            if (diff > 100) {
                log.info("📊 Significant repair: {} characters changed", diff);
            }
        }
    }
    
    /**
     * Get detailed syntax errors from JSON (for error reporting)
     */
    public List<String> getSyntaxErrors(String json) {
        List<String> errors = new ArrayList<>();
        
        try (JsonParser parser = jsonFactory.createParser(json)) {
            while (parser.nextToken() != null) {
                // Just iterate to find errors
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            
            // Extract line/column info if available
            if (msg != null) {
                errors.add(msg);
            }
        }
        
        return errors;
    }

    /**
     * Check if auto-correction is enabled
     */
    public boolean isEnabled() {
        return autoCorrectionEnabled;
    }
}
