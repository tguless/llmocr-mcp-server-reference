package com.llmocr.mcp.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for customer allocation requests in MCP tools
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAllocation {
    
    @JsonProperty("customerId")
    private String customerId;
    
    @JsonProperty("usageKWh")
    private BigDecimal usageKWh;
}

