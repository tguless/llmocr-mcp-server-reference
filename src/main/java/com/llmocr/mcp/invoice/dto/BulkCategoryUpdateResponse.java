package com.llmocr.mcp.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCategoryUpdateResponse {
    private boolean success;
    private int itemsUpdated;
    private int itemsFailed;
    private int itemsNotFound;
    private List<Long> updatedIds;
    private long processingTimeMs;
}
