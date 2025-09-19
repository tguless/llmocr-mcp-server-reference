package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.AuthorizedClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorizedClientRepository extends JpaRepository<AuthorizedClient, Long> {

    /**
     * Find authorized client by tenant and client ID
     */
    Optional<AuthorizedClient> findByTenantIdAndClientIdAndIsActiveTrue(String tenantId, String clientId);

    /**
     * Find all authorized clients for a tenant
     */
    List<AuthorizedClient> findByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Check if a client has a specific scope for a tenant
     */
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
           "FROM AuthorizedClient ac JOIN ac.scopes cs " +
           "WHERE ac.tenantId = :tenantId AND ac.clientId = :clientId " +
           "AND cs.scope = :scope AND ac.isActive = true AND cs.isActive = true")
    boolean hasClientScope(@Param("tenantId") String tenantId, 
                          @Param("clientId") String clientId, 
                          @Param("scope") String scope);

    /**
     * Get all scopes for a client in a tenant
     */
    @Query("SELECT cs.scope FROM AuthorizedClient ac JOIN ac.scopes cs " +
           "WHERE ac.tenantId = :tenantId AND ac.clientId = :clientId " +
           "AND ac.isActive = true AND cs.isActive = true")
    List<String> getClientScopes(@Param("tenantId") String tenantId, 
                                @Param("clientId") String clientId);
}
