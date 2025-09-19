package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.service.InvoiceToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple REST endpoint for tool discovery
 * 
 * This is a temporary bridge to allow our custom MCP client to discover
 * tools from the Spring AI MCP server until we can migrate to using
 * Spring AI's built-in MCP client.
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "http://localhost:*", allowedHeaders = "*", allowCredentials = "true")
public class ToolDiscoveryController {

    private final ToolCallbackProvider toolCallbackProvider;

    /**
     * Simple tool discovery endpoint that returns available tools
     * This mimics what our client expects to receive
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        try {
            log.debug("Tool discovery request received");
            
            // Get tool callbacks from Spring AI
            ToolCallback[] toolCallbackArray = toolCallbackProvider.getToolCallbacks();
            List<ToolCallback> toolCallbacks = List.of(toolCallbackArray);
            
            // Convert to the format our client expects
            List<Map<String, Object>> tools = toolCallbacks.stream()
                .map(callback -> {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("name", callback.getToolDefinition().name());
                    tool.put("description", callback.getToolDefinition().description());
                    tool.put("inputSchema", callback.getToolDefinition().inputSchema());
                    return tool;
                })
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("tools", tools);
            
            log.info("Returning {} tools for discovery", tools.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to list tools: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to list tools"));
        }
    }

    /**
     * Tool execution endpoint for REST-based MCP clients
     * This bridges the gap between REST clients and Spring AI MCP servers
     */
    @PostMapping("/tools/call")
    public ResponseEntity<String> executeTool(@RequestBody Map<String, Object> request) {
        try {
            log.debug("Tool execution request received: {}", request);
            
            String toolName = (String) request.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
            
            if (toolName == null) {
                return ResponseEntity.badRequest().body("Tool name is required");
            }
            
            if (arguments == null) {
                arguments = new HashMap<>();
            }
            
            log.info("Executing tool: {} with arguments: {}", toolName, arguments);
            
            // Get tool callbacks from Spring AI
            ToolCallback[] toolCallbackArray = toolCallbackProvider.getToolCallbacks();
            List<ToolCallback> toolCallbacks = List.of(toolCallbackArray);
            
            // Find the requested tool
            ToolCallback targetTool = toolCallbacks.stream()
                .filter(callback -> callback.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElse(null);
            
            if (targetTool == null) {
                log.warn("Tool not found: {}", toolName);
                return ResponseEntity.status(404).body("Tool not found: " + toolName);
            }
            
            // Execute the tool
            String argumentsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(arguments);
            String result = targetTool.call(argumentsJson);
            
            log.info("Tool {} executed successfully, result length: {}", toolName, result != null ? result.length() : 0);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Tool execution failed: " + e.getMessage());
        }
    }
}
