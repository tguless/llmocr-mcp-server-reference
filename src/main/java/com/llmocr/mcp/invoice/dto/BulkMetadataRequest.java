package com.llmocr.mcp.invoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkMetadataRequest {
    private Long invoiceId;
    private Map<String, String> metadata;
    @Default
    private Boolean skipExistingKeys = false;
}
