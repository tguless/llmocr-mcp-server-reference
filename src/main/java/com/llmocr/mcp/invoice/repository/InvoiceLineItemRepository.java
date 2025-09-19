package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

    List<InvoiceLineItem> findByInvoiceIdOrderByLineNumber(Long invoiceId);

    @Query("SELECT li FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId " +
           "AND li.invoice.tenantId = :tenantId ORDER BY li.lineNumber")
    List<InvoiceLineItem> findByInvoiceIdAndTenantIdOrderByLineNumber(
            @Param("invoiceId") Long invoiceId,
            @Param("tenantId") String tenantId);

    @Query("SELECT COUNT(li) FROM InvoiceLineItem li WHERE li.invoice.id = :invoiceId")
    long countByInvoiceId(@Param("invoiceId") Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);
}
