package com.llmocr.mcp.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_scopes", schema = "mcp_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ClientScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_client_id", nullable = false)
    private AuthorizedClient authorizedClient;

    @Column(name = "scope", nullable = false, length = 100)
    private String scope;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Generic scopes (server-agnostic for BYOMCP)
    public static final String READ_ALL = "read:*";
    public static final String WRITE_ALL = "write:*";
    public static final String DELETE_ALL = "delete:*";
    public static final String ADMIN_ALL = "admin:*";
    
    // Legacy invoice-specific scopes (for backward compatibility)
    public static final String READ_INVOICES = "read:invoices";
    public static final String WRITE_INVOICES = "write:invoices";
    public static final String DELETE_INVOICES = "delete:invoices";
    public static final String ADMIN_INVOICES = "admin:invoices";
}
