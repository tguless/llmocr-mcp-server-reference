package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    Optional<Tenant> findByTenantIdAndActiveTrue(String tenantId);

    List<Tenant> findByActiveTrue();

    boolean existsByTenantId(String tenantId);

    @Query("SELECT t FROM Tenant t WHERE t.tenantName LIKE %:name% AND t.active = true")
    List<Tenant> findByTenantNameContainingAndActiveTrue(@Param("name") String name);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.active = true")
    long countActiveTenants();
}
