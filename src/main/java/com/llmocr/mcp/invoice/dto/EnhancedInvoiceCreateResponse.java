package com.llmocr.mcp.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for enhanced invoice creation
 * Contains results of invoice creation, line items, and metadata storage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EnhancedInvoiceCreateResponse {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("invoiceId")
    private Long invoiceId;

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("invoiceCreated")
    private Boolean invoiceCreated;

    @JsonProperty("lineItemsAdded")
    private LineItemsResult lineItemsAdded;

    @JsonProperty("metadataStored")
    private MetadataResult metadataStored;

    @JsonProperty("message")
    private String message;

    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    /**
     * Line items operation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class LineItemsResult {
        @JsonProperty("count")
        private Integer count;

        @JsonProperty("created")
        private Integer created;

        @JsonProperty("skipped")
        private Integer skipped;

        @JsonProperty("lineItemIds")
        private List<Long> lineItemIds;

        @JsonProperty("categorization")
        private Map<String, Integer> categorization;

        @JsonProperty("highConfidence")
        private Integer highConfidence;

        @JsonProperty("lowConfidence")
        private Integer lowConfidence;

        @JsonProperty("itemsRequiringReview")
        private List<ReviewItem> itemsRequiringReview;
    }

    /**
     * Item requiring review
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ReviewItem {
        @JsonProperty("lineItemId")
        private Long lineItemId;

        @JsonProperty("description")
        private String description;

        @JsonProperty("suggestedCategory")
        private String suggestedCategory;

        @JsonProperty("confidence")
        private Double confidence;
    }

    /**
     * Metadata operation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class MetadataResult {
        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("count")
        private Integer count;

        @JsonProperty("stored")
        private Integer stored;

        @JsonProperty("updated")
        private Integer updated;

        @JsonProperty("skipped")
        private Integer skipped;

        @JsonProperty("storedKeys")
        private List<String> storedKeys;

        @JsonProperty("error")
        private String error;
    }
}
