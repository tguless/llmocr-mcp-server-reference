package com.llmocr.mcp.invoice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-report")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuditReportController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<?> getAuditReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String meterNumber,
            @RequestParam(defaultValue = "invoice_number") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            HttpServletRequest request) {
        try {
            String tenantId = (String) request.getAttribute("tenantId");
            
            log.info("Fetching audit report for tenant: {}, page: {}, pageSize: {}, invoice: {}, meter: {}, sortBy: {}, sortDirection: {}", 
                    tenantId, page, pageSize, invoiceNumber, meterNumber, sortBy, sortDirection);

            // Build WHERE clause with filters
            StringBuilder whereClause = new StringBuilder("WHERE i.tenant_id = ?");
            java.util.List<Object> params = new java.util.ArrayList<>();
            params.add(tenantId);

            if (invoiceNumber != null && !invoiceNumber.trim().isEmpty()) {
                whereClause.append(" AND i.invoice_number ILIKE ?");
                params.add("%" + invoiceNumber.trim() + "%");
            }
            if (meterNumber != null && !meterNumber.trim().isEmpty()) {
                whereClause.append(" AND m.metadata->>'meter_number' ILIKE ?");
                params.add("%" + meterNumber.trim() + "%");
            }

            // Count total rows
            String countSql = "SELECT COUNT(*) FROM (SELECT 1 FROM mcp_invoice.invoices i " +
                    "LEFT JOIN (\n" +
                    "        SELECT\n" +
                    "            invoice_id,\n" +
                    "            tenant_id,\n" +
                    "            JSONB_OBJECT_AGG(metadata_key, metadata_value) as metadata\n" +
                    "        FROM mcp_invoice.invoice_metadata\n" +
                    "        GROUP BY invoice_id, tenant_id\n" +
                    "    ) m ON i.id = m.invoice_id AND i.tenant_id = m.tenant_id " +
                    whereClause + ") as cnt";
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // Validate and build ORDER BY clause
            String orderByClause = buildOrderByClause(sortBy, sortDirection);

            // Get paginated data
            String sql = "SELECT\n" +
                    "    i.id as invoice_id,\n"
                    + "    i.invoice_number,\n" +
                    "    i.service_period_start_date,\n" +
                    "    i.service_period_end_date,\n" +
                    "    m.metadata->>'meter_number' as meter_number,\n" +
                    "    m.metadata->>'generated_kwh' as generated_kwh,\n" +
                    "    CASE\n" +
                    "        WHEN m.metadata->>'contracted_rate' IS NOT NULL\n" +
                    "            THEN -(ABS((m.metadata->>'contracted_rate')::NUMERIC))\n" +
                    "        WHEN m.metadata->>'contracted_dg_rate' IS NOT NULL\n" +
                    "            THEN -(ABS((m.metadata->>'contracted_dg_rate')::NUMERIC))\n" +
                    "        ELSE NULL\n" +
                    "        END as contracted_rate,\n" +
                    "    totGen.total as \"DG Credit\",\n" +
                    "    totMisc.total as \"$ Misc\",\n" +
                    "    i.total_amount as \"Invoice $\"\n" +
                    "FROM\n" +
                    "    mcp_invoice.invoices i\n" +
                    "        LEFT JOIN (\n" +
                    "        SELECT\n" +
                    "            invoice_id,\n" +
                    "            SUM(line_total) as total\n" +
                    "        FROM mcp_invoice.invoice_line_items\n" +
                    "        WHERE UPPER(category) <> 'CREDIT_GENERATION'\n" +
                    "        GROUP BY invoice_id\n" +
                    "    ) totMisc ON i.id = totMisc.invoice_id\n" +
                    "        LEFT JOIN (\n" +
                    "        SELECT\n" +
                    "            invoice_id,\n" +
                    "            SUM(line_total) as total\n" +
                    "        FROM mcp_invoice.invoice_line_items\n" +
                    "        WHERE UPPER(category) = 'CREDIT_GENERATION'\n" +
                    "        GROUP BY invoice_id\n" +
                    "    ) totGen ON totGen.invoice_id = i.id\n" +
                    "        LEFT JOIN (\n" +
                    "        SELECT\n" +
                    "            invoice_id,\n" +
                    "            tenant_id,\n" +
                    "            JSONB_OBJECT_AGG(metadata_key, metadata_value) as metadata\n" +
                    "        FROM mcp_invoice.invoice_metadata\n" +
                    "        GROUP BY invoice_id, tenant_id\n" +
                    "    ) m ON i.id = m.invoice_id AND i.tenant_id = m.tenant_id\n" +
                    whereClause + "\n" +
                    orderByClause + "\n" +
                    "LIMIT ? OFFSET ?";

            params.add(pageSize);
            params.add(page * pageSize);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

            log.info("Fetched {} audit report rows for tenant: {} with sort: {} {}", rows.size(), tenantId, sortBy, sortDirection);

            Map<String, Object> response = new HashMap<>();
            response.put("rows", rows);
            response.put("total", totalCount);
            response.put("page", page);
            response.put("pageSize", pageSize);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch audit report: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to fetch audit report: " + e.getMessage()
            ));
        }
    }

    /**
     * Build ORDER BY clause with validation to prevent SQL injection
     */
    private String buildOrderByClause(String sortBy, String sortDirection) {
        // Whitelist allowed sort fields
        String[] allowedFields = {
                "invoice_number", 
                "service_period_start_date", 
                "service_period_end_date", 
                "meter_number", 
                "generated_kwh", 
                "contracted_rate", 
                "DG Credit", 
                "$ Misc", 
                "Invoice $"
        };
        
        // Validate sortBy field
        String validatedSortBy = "invoice_number";
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                // Map special column names and handle numeric casting
                if (field.equalsIgnoreCase("DG Credit")) {
                    validatedSortBy = "\"DG Credit\"";
                } else if (field.equalsIgnoreCase("$ Misc")) {
                    validatedSortBy = "\"$ Misc\"";
                } else if (field.equalsIgnoreCase("Invoice $")) {
                    validatedSortBy = "\"Invoice $\"";
                } else if (field.equalsIgnoreCase("generated_kwh")) {
                    // Cast to NUMERIC for proper numeric sorting (not string sorting)
                    validatedSortBy = "(m.metadata->>'generated_kwh')::NUMERIC";
                } else if (field.equalsIgnoreCase("contracted_rate")) {
                    // Already numeric from CASE statement, but ensure numeric sort
                    validatedSortBy = "contracted_rate::NUMERIC";
                } else {
                    validatedSortBy = field;
                }
                break;
            }
        }
        
        // Validate sort direction
        String validatedDirection = sortDirection.equalsIgnoreCase("desc") ? "DESC" : "ASC";
        
        return "ORDER BY " + validatedSortBy + " " + validatedDirection;
    }
}

