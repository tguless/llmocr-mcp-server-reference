package com.llmocr.mcp.invoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP Client Configuration for MCP Server
 * 
 * Separate configuration to avoid circular dependencies with security configuration.
 */
@Configuration
public class HttpClientConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
