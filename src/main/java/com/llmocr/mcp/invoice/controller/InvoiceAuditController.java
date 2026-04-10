package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.McpAuditLog;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.McpAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for invoice audit trail
 * Shows what MCP tools were called during invoice processing
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceAuditController {

    private final InvoiceRepository invoiceRepository;
    private final McpAuditLogRepository auditLogRepository;

    /**
     * Get MCP tool call history for an invoice
     * 
     * This shows what AI operations were performed during invoice processing
     */
    @GetMapping("/{invoiceId}/audit-trail")
    public ResponseEntity<Map<String, Object>> getInvoiceAuditTrail(
            @PathVariable Long invoiceId,
            @RequestAttribute("tenantId") String tenantId) {
        
        log.debug("Getting audit trail for invoice {} in tenant {}", invoiceId, tenantId);

        // Verify invoice exists and belongs to tenant
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        // Get audit logs for this invoice's source file
        List<McpAuditLog> auditLogs;
        
        if (invoice.getSourceFileName() != null && !invoice.getSourceFileName().isEmpty()) {
            // Query by source filename
            auditLogs = auditLogRepository.findByTenantIdAndSourceFilenameOrderByCreatedAtAsc(
                    tenantId, invoice.getSourceFileName());
            log.debug("Found {} audit log entries for invoice {} (filename: {})", 
                     auditLogs.size(), invoiceId, invoice.getSourceFileName());
        } else {
            // No source filename - return empty list
            auditLogs = List.of();
            log.warn("Invoice {} has no source filename, cannot retrieve audit trail", invoiceId);
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("invoiceId", invoiceId);
        response.put("sourceFileName", invoice.getSourceFileName());
        response.put("totalToolCalls", auditLogs.size());
        
        // Convert audit logs to simplified format
        List<Map<String, Object>> toolCalls = auditLogs.stream()
                .map(log -> {
                    Map<String, Object> call = new HashMap<>();
                    call.put("toolName", log.getToolName());
                    call.put("operationType", log.getOperationType().name());
                    call.put("success", log.getSuccess());
                    call.put("executionTimeMs", log.getExecutionTimeMs());
                    call.put("timestamp", log.getCreatedAt());
                    call.put("createdBy", log.getCreatedBy());
                    call.put("errorMessage", log.getErrorMessage());
                    call.put("jobId", log.getJobId());
                    call.put("s3Bucket", log.getS3Bucket());
                    call.put("s3ObjectKey", log.getS3ObjectKey());
                    return call;
                })
                .collect(Collectors.toList());
        
        response.put("toolCalls", toolCalls);

        // Calculate statistics
        long successfulCalls = auditLogs.stream().filter(McpAuditLog::getSuccess).count();
        long failedCalls = auditLogs.size() - successfulCalls;
        Double avgExecutionTime = auditLogs.stream()
                .filter(l -> l.getExecutionTimeMs() != null)
                .mapToLong(McpAuditLog::getExecutionTimeMs)
                .average()
                .orElse(0.0);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("successfulCalls", successfulCalls);
        statistics.put("failedCalls", failedCalls);
        statistics.put("averageExecutionTimeMs", Math.round(avgExecutionTime));
        
        response.put("statistics", statistics);

        return ResponseEntity.ok(response);
    }
}

