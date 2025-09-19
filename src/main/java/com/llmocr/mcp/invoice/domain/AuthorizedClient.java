package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "authorized_clients", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuthorizedClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "trusted_issuer", nullable = false, length = 500)
    private String trustedIssuer;

    @Column(name = "introspection_endpoint", nullable = false, length = 500)
    private String introspectionEndpoint;

    @Column(name = "jwks_endpoint", length = 500)
    private String jwksEndpoint;

    @Column(name = "token_endpoint", length = 500)
    private String tokenEndpoint;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // Relationship to scopes
    @OneToMany(mappedBy = "authorizedClient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ClientScope> scopes;

    /**
     * Check if this client has a specific scope
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.stream()
                .anyMatch(s -> s.getScope().equals(scope) && s.getIsActive());
    }
}
