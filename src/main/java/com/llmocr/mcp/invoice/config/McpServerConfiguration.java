package com.llmocr.mcp.invoice.config;

import com.llmocr.mcp.invoice.service.BulkCategoryToolService;
import com.llmocr.mcp.invoice.service.BulkLineItemToolService;
import com.llmocr.mcp.invoice.service.BulkMetadataToolService;
import com.llmocr.mcp.invoice.service.CategoryDiscoveryToolService;
import com.llmocr.mcp.invoice.service.InvoiceMetadataToolService;
import com.llmocr.mcp.invoice.service.InvoiceToolService;
import com.llmocr.mcp.invoice.service.InvoiceUpdateToolService;
import com.llmocr.mcp.invoice.service.InvoiceValidationToolService;
import com.llmocr.mcp.invoice.service.LineItemToolService;
import com.llmocr.mcp.invoice.service.MetadataKeyDiscoveryToolService;
import com.llmocr.mcp.invoice.service.RawJsonTestToolService;
import com.llmocr.mcp.invoice.service.VendorToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Configuration for Invoice Processing
 * 
 * This configuration sets up the MCP server with tool callbacks for invoice processing.
 * Based on Spring AI MCP Server Boot Starter documentation.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpServerConfiguration {

    private final InvoiceToolService invoiceToolService;
    private final InvoiceUpdateToolService invoiceUpdateToolService;
    private final VendorToolService vendorToolService;
    private final LineItemToolService lineItemToolService;
    private final CategoryDiscoveryToolService categoryDiscoveryToolService;
    private final MetadataKeyDiscoveryToolService metadataKeyDiscoveryToolService;
    private final InvoiceMetadataToolService invoiceMetadataToolService;
    private final InvoiceValidationToolService invoiceValidationToolService;
    private final BulkLineItemToolService bulkLineItemToolService;
    private final BulkMetadataToolService bulkMetadataToolService;
    private final BulkCategoryToolService bulkCategoryToolService;
    private final RawJsonTestToolService rawJsonTestToolService;

    /**
     * Configure all invoice processing tools for stateless MCP server
     * 
     * Following Spring AI 1.1 documentation for stateless servers:
     * https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-stateless-server-boot-starter-docs.html
     * 
     * Registers all tool services:
     * - InvoiceToolService: Basic invoice CRUD operations (4 tools)
     * - InvoiceUpdateToolService: Update invoice fields (1 tool) NEW
     * - VendorToolService: Vendor lookup and management (3 tools)
     * - LineItemToolService: Line item processing with auto-categorization (5 tools)
     * - CategoryDiscoveryToolService: Category discovery and rules (3 tools)
     * - MetadataKeyDiscoveryToolService: Metadata key discovery and validation (2 tools)
     * - InvoiceMetadataToolService: Custom key-value metadata storage (4 tools)
     * - InvoiceValidationToolService: Invoice completeness validation (1 tool)
     * - BulkLineItemToolService: Bulk line item operations - 22.4x faster (1 tool)
     * - BulkMetadataToolService: Bulk metadata operations - 12.5x faster (1 tool)
     * - BulkCategoryToolService: Bulk category updates - 4.7x faster (1 tool)
     */
    @Bean
    public ToolCallbackProvider invoiceProcessingTools() {
        log.info("Configuring ALL invoice processing MCP tools for stateless server");
        log.info("  - InvoiceToolService: 4 tools");
        log.info("  - InvoiceUpdateToolService: 1 tool (updateInvoice - fix validation errors) NEW");
        log.info("  - VendorToolService: 3 tools");
        log.info("  - LineItemToolService: 5 tools");
        log.info("  - CategoryDiscoveryToolService: 3 tools");
        log.info("  - MetadataKeyDiscoveryToolService: 2 tools (getAvailableMetadataKeys, validateMetadataKey)");
        log.info("  - InvoiceMetadataToolService: 4 tools");
        log.info("  - InvoiceValidationToolService: 1 tool (validateInvoice - FINAL CHECKPOINT)");
        log.info("  - BulkLineItemToolService: 1 tool (addInvoiceLineItemsBulk - 22.4x faster)");
        log.info("  - BulkMetadataToolService: 1 tool (storeInvoiceMetadataBulk - 12.5x faster)");
        log.info("  - BulkCategoryToolService: 1 tool (updateLineItemCategoriesBulk - 4.7x faster)");
        log.info("  - RawJsonTestToolService: 1 tool (llmOcrRawJsonTest - paired schema header validation) NEW");
        log.info("Total: 27 tools registered (including llmOcrRawJsonTest for schema testing)");
        
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                    invoiceToolService,
                    invoiceUpdateToolService,
                    vendorToolService,
                    lineItemToolService,
                    categoryDiscoveryToolService,
                    metadataKeyDiscoveryToolService,
                    invoiceMetadataToolService,
                    invoiceValidationToolService,
                    bulkLineItemToolService,
                    bulkMetadataToolService,
                    bulkCategoryToolService,
                    rawJsonTestToolService
                )
                .build();
    }

    /**
     * The stateless MCP server auto-configuration will handle:
     * - Tool registration and discovery
     * - Request routing and processing  
     * - Authentication and security
     * - CORS configuration
     */
}
