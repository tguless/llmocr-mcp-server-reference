package com.llmocr.mcp.invoice.config;

import com.llmocr.mcp.invoice.service.InvoiceToolService;
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

    /**
     * Configure invoice processing tools for stateless MCP server
     * 
     * Following Spring AI 1.1 documentation for stateless servers:
     * https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-stateless-server-boot-starter-docs.html
     */
    @Bean
    public ToolCallbackProvider invoiceProcessingTools() {
        log.info("Configuring invoice processing MCP tools for stateless server");
        
        return MethodToolCallbackProvider.builder()
                .toolObjects(invoiceToolService)
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
