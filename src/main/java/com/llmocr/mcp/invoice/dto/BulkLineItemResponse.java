package com.llmocr.mcp.invoice.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for bulk line item operations
 * 
 * Contains processing results, statistics, and items requiring manual review.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkLineItemResponse {

    /**
     * Whether the operation was successful
     */
    private Boolean success;

    /**
     * The invoice ID that was processed
     */
    private Long invoiceId;

    /**
     * Total number of line items in the request
     */
    private Integer itemsProcessed;

    /**
     * Number of line items successfully created
     */
    private Integer itemsCreated;

    /**
     * Number of line items skipped (duplicates, errors, etc.)
     */
    private Integer itemsSkipped;

    /**
     * Map of categories to counts of items categorized in each
     */
    private Map<String, Integer> categorization;

    /**
     * Number of items with high confidence categorization (>=85%)
     */
    private Integer highConfidence;

    /**
     * Number of items with low confidence categorization (<85%)
     */
    private Integer lowConfidence;

    /**
     * List of created line item IDs
     */
    private List<Long> lineItemIds;

    /**
     * Items that require manual review (low confidence categorization)
     */
    private List<ReviewItem> itemsRequiringReview;

    /**
     * Total processing time in milliseconds
     */
    private Long processingTimeMs;

    /**
     * Inner class representing an item requiring review
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewItem {
        /**
         * The line item ID that requires review
         */
        private Long lineItemId;

        /**
         * Description of the line item
         */
        private String description;

        /**
         * The category suggested by the AI categorization service
         */
        private String suggestedCategory;

        /**
         * The confidence level of the categorization (0.0 to 1.0)
         */
        private Double confidence;
    }
}
