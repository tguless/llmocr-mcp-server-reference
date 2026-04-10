package com.llmocr.mcp.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating invoice with inline line items and metadata
 * Combines invoice creation, line item addition, and metadata storage in one call
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EnhancedInvoiceCreateRequest {

    // Invoice fields
    @JsonProperty("vendorId")
    private Long vendorId;

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("vendorName")
    private String vendorName;

    @JsonProperty("vendorAddress")
    private String vendorAddress;

    @JsonProperty("customerName")
    private String customerName;

    @JsonProperty("invoiceDate")
    private String invoiceDate;

    @JsonProperty("dueDate")
    private String dueDate;

    @JsonProperty("totalAmount")
    private String totalAmount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("description")
    private String description;

    @JsonProperty("servicePeriodStartDate")
    private String servicePeriodStartDate;

    @JsonProperty("servicePeriodEndDate")
    private String servicePeriodEndDate;

    @JsonProperty("sourceFileName")
    private String sourceFileName;

    @JsonProperty("s3BucketName")
    private String s3BucketName;

    @JsonProperty("s3ObjectKey")
    private String s3ObjectKey;

    // Optional: Line items to add
    @JsonProperty("lineItems")
    private List<LineItemInput> lineItems;

    // Optional: Metadata to store
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    // Optional: Skip existing metadata keys
    @JsonProperty("skipExistingKeys")
    private Boolean skipExistingKeys;

    /**
     * Line item input for inline addition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class LineItemInput {
        @JsonProperty("lineNumber")
        private Integer lineNumber;

        @JsonProperty("description")
        private String description;

        @JsonProperty("amount")
        private String amount;

        @JsonProperty("quantity")
        private String quantity;

        @JsonProperty("unitOfMeasure")
        private String unitOfMeasure;

        @JsonProperty("unitPrice")
        private String unitPrice;

        @JsonProperty("category")
        private String category;
    }
}
