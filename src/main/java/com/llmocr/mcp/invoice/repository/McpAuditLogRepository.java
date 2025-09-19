package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.McpAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface McpAuditLogRepository extends JpaRepository<McpAuditLog, Long> {

    List<McpAuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Page<McpAuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<McpAuditLog> findByTenantIdAndOperationType(String tenantId, McpAuditLog.OperationType operationType);

    List<McpAuditLog> findByTenantIdAndClientId(String tenantId, String clientId);

    List<McpAuditLog> findByTenantIdAndSessionId(String tenantId, String sessionId);

    @Query("SELECT a FROM McpAuditLog a WHERE a.tenantId = :tenantId " +
           "AND a.createdAt >= :startTime AND a.createdAt <= :endTime " +
           "ORDER BY a.createdAt DESC")
    List<McpAuditLog> findByTenantIdAndTimeRange(
            @Param("tenantId") String tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(a) FROM McpAuditLog a WHERE a.tenantId = :tenantId " +
           "AND a.success = :success")
    long countByTenantIdAndSuccess(@Param("tenantId") String tenantId, @Param("success") Boolean success);

    @Query("SELECT a.operationType, COUNT(a) FROM McpAuditLog a " +
           "WHERE a.tenantId = :tenantId " +
           "GROUP BY a.operationType")
    List<Object[]> getOperationTypeStatsByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT AVG(a.executionTimeMs) FROM McpAuditLog a " +
           "WHERE a.tenantId = :tenantId AND a.operationType = :operationType " +
           "AND a.executionTimeMs IS NOT NULL")
    Double getAverageExecutionTimeByTenantIdAndOperationType(
            @Param("tenantId") String tenantId,
            @Param("operationType") McpAuditLog.OperationType operationType);
}
