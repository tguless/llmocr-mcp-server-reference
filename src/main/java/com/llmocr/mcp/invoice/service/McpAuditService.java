package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.McpAuditLog;
import com.llmocr.mcp.invoice.repository.McpAuditLogRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Removed MCP annotation dependency - using REST controller approach
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Audit Service
 * 
 * Provides audit logging for MCP operations and tools for audit queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpAuditService {

    private final McpAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(String operationType, String toolName, boolean success, String message, long startTime) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();
            String userId = McpSecurityContext.getCurrentUserId();
            String clientId = McpSecurityContext.getCurrentClientId();
            
            long executionTime = System.currentTimeMillis() - startTime;

            McpAuditLog auditLog = McpAuditLog.builder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .operationType(McpAuditLog.OperationType.valueOf(operationType))
                    .toolName(toolName)
                    .success(success)
                    .errorMessage(success ? null : message)
                    .executionTimeMs(executionTime)
                    .createdBy(userId)
                    .ipAddress(McpSecurityContext.getClientIpAddress())
                    .userAgent("MCP-Server/1.0")  // Default user agent for MCP server operations
                    .build();

            auditLogRepository.save(auditLog);
            
            log.debug("Audit log created for operation {} by client {} in tenant {}", 
                    operationType, clientId, tenantId);

        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage(), e);
            // Don't throw exception to avoid disrupting the main operation
        }
    }

    // Audit logs tool moved to REST controller
    @Transactional(readOnly = true)
    public String getAuditLogs(int page, int size) {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        
        try {
            log.debug("Getting audit logs for tenant {}", tenantId);

            if (page < 0) page = 0;
            if (size <= 0 || size > 100) size = 20;

            // For simplicity, returning count and recent operations
            long totalOperations = auditLogRepository.countByTenantIdAndSuccess(tenantId, true);
            long failedOperations = auditLogRepository.countByTenantIdAndSuccess(tenantId, false);
            
            Map<String, Object> auditSummary = new HashMap<>();
            auditSummary.put("totalOperations", totalOperations);
            auditSummary.put("failedOperations", failedOperations);
            auditSummary.put("successRate", totalOperations > 0 ? 
                    (double)(totalOperations - failedOperations) / totalOperations * 100 : 0.0);

            logOperation("TOOL_CALL", "getAuditLogs", true, "Audit logs retrieved successfully", startTime);

            return "SUCCESS: " + auditSummary.toString();

        } catch (Exception e) {
            log.error("Failed to get audit logs for tenant {}: {}", tenantId, e.getMessage(), e);
            logOperation("TOOL_CALL", "getAuditLogs", false, e.getMessage(), startTime);
            return "ERROR: " + e.getMessage();
        }
    }

    // Operation statistics tool moved to REST controller
    @Transactional(readOnly = true)
    public String getOperationStatistics() {
        long startTime = System.currentTimeMillis();
        String tenantId = McpSecurityContext.getCurrentTenantId();
        
        try {
            log.debug("Getting operation statistics for tenant {}", tenantId);

            // Get average execution times for different operations
            Double avgProcessInvoiceTime = auditLogRepository.getAverageExecutionTimeByTenantIdAndOperationType(
                    tenantId, McpAuditLog.OperationType.TOOL_CALL);

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("averageExecutionTimeMs", avgProcessInvoiceTime != null ? avgProcessInvoiceTime : 0.0);
            statistics.put("tenantId", tenantId);

            logOperation("TOOL_CALL", "getOperationStatistics", true, "Statistics retrieved successfully", startTime);

            return "SUCCESS: " + statistics.toString();

        } catch (Exception e) {
            log.error("Failed to get operation statistics for tenant {}: {}", tenantId, e.getMessage(), e);
            logOperation("TOOL_CALL", "getOperationStatistics", false, e.getMessage(), startTime);
            return "ERROR: " + e.getMessage();
        }
    }
}
