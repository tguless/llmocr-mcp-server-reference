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
public class BulkMetadataResponse {
    private boolean success;
    private Long invoiceId;
    private int metadataStored;
    private int metadataUpdated;
    private int metadataSkipped;
    private List<String> storedKeys;
    private long processingTimeMs;
}
