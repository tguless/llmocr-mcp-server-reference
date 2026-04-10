package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.MetadataKeyDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Service for validating metadata values against their defined data types
 */
@Service
@Slf4j
public class MetadataValidationService {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    
    // Common date formats to try
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ISO_LOCAL_DATE,           // 2025-01-15
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),  // 01/15/2025
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),  // 15/01/2025
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),  // 2025/01/15
        DateTimeFormatter.ofPattern("MMM dd, yyyy") // Jan 15, 2025
    };

    /**
     * Validates a metadata value against its key definition
     * 
     * @param keyDefinition The metadata key definition with data type
     * @param value The value to validate
     * @return ValidationResult with success flag and error message if validation fails
     */
    public ValidationResult validate(MetadataKeyDefinition keyDefinition, String value) {
        if (value == null || value.trim().isEmpty()) {
            if (Boolean.TRUE.equals(keyDefinition.getRequired())) {
                return ValidationResult.failure(
                    String.format("Value is required for key '%s'", keyDefinition.getKeyCode())
                );
            }
            return ValidationResult.success();
        }

        String dataType = keyDefinition.getDataType();
        if (dataType == null || dataType.trim().isEmpty()) {
            // No data type specified, accept any value
            return ValidationResult.success();
        }

        String trimmedValue = value.trim();
        
        switch (dataType.toUpperCase()) {
            case "STRING":
                return ValidationResult.success(); // Any string is valid
                
            case "NUMBER":
            case "INTEGER":
                return validateInteger(keyDefinition.getKeyCode(), trimmedValue);
                
            case "DECIMAL":
                return validateDecimal(keyDefinition.getKeyCode(), trimmedValue);
                
            case "PERCENTAGE":
                return validatePercentage(keyDefinition.getKeyCode(), trimmedValue);
                
            case "DATE":
                return validateDate(keyDefinition.getKeyCode(), trimmedValue);
                
            case "BOOLEAN":
                return validateBoolean(keyDefinition.getKeyCode(), trimmedValue);
                
            default:
                log.warn("Unknown data type '{}' for key '{}', accepting value", 
                    dataType, keyDefinition.getKeyCode());
                return ValidationResult.success();
        }
    }

    private ValidationResult validateInteger(String keyCode, String value) {
        if (!INTEGER_PATTERN.matcher(value).matches()) {
            return ValidationResult.failure(
                String.format("Invalid integer value '%s' for key '%s'. Expected a whole number (e.g., 123 or -456).", 
                    value, keyCode)
            );
        }
        
        try {
            Long.parseLong(value);
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure(
                String.format("Integer value '%s' is out of range for key '%s'", value, keyCode)
            );
        }
    }

    private ValidationResult validateDecimal(String keyCode, String value) {
        if (!DECIMAL_PATTERN.matcher(value).matches()) {
            return ValidationResult.failure(
                String.format("Invalid decimal value '%s' for key '%s'. Expected a decimal number (e.g., 0.0482 or -0.0493).", 
                    value, keyCode)
            );
        }
        
        try {
            new BigDecimal(value);
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure(
                String.format("Decimal value '%s' is invalid for key '%s': %s", value, keyCode, e.getMessage())
            );
        }
    }

    private ValidationResult validatePercentage(String keyCode, String value) {
        if (!PERCENTAGE_PATTERN.matcher(value).matches()) {
            return ValidationResult.failure(
                String.format("Invalid percentage value '%s' for key '%s'. Expected a number (e.g., 95.5 or -2.3).", 
                    value, keyCode)
            );
        }
        
        try {
            BigDecimal percentage = new BigDecimal(value);
            // Optionally validate range (e.g., -100 to 100 or 0 to 100)
            // For now, accept any numeric value
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure(
                String.format("Percentage value '%s' is invalid for key '%s': %s", value, keyCode, e.getMessage())
            );
        }
    }

    private ValidationResult validateDate(String keyCode, String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(value, formatter);
                return ValidationResult.success();
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        return ValidationResult.failure(
            String.format("Invalid date value '%s' for key '%s'. Expected formats: YYYY-MM-DD, MM/DD/YYYY, etc.", 
                value, keyCode)
        );
    }

    private ValidationResult validateBoolean(String keyCode, String value) {
        String lowerValue = value.toLowerCase();
        if (lowerValue.equals("true") || lowerValue.equals("false") || 
            lowerValue.equals("yes") || lowerValue.equals("no") ||
            lowerValue.equals("1") || lowerValue.equals("0")) {
            return ValidationResult.success();
        }
        
        return ValidationResult.failure(
            String.format("Invalid boolean value '%s' for key '%s'. Expected: true, false, yes, no, 1, or 0.", 
                value, keyCode)
        );
    }

    /**
     * Result of metadata validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

