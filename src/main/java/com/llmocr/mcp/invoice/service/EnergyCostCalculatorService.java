package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service for calculating energy costs and pass-through charges
 * for third-party billing audit workflows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnergyCostCalculatorService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;

    /**
     * Calculate total pass-through charges for an invoice
     */
    @Transactional(readOnly = true)
    public PassThroughSummary calculatePassThroughCharges(Long invoiceId) {
        log.debug("Calculating pass-through charges for invoice {}", invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceIdOrderByLineNumber(invoiceId);

        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        BigDecimal totalPassThrough = BigDecimal.ZERO;
        BigDecimal totalGenerationCredits = BigDecimal.ZERO;
        BigDecimal totalUsageKWh = BigDecimal.ZERO;

        for (InvoiceLineItem item : lineItems) {
            if (item.getCategory() == null) continue;

            // Sum by category (category is now String code)
            categoryTotals.merge(item.getCategory(), item.getLineTotal(), BigDecimal::add);

            // Sum pass-through charges
            if (item.isPassThrough() && !item.getCategory().startsWith("CREDIT_")) {
                totalPassThrough = totalPassThrough.add(item.getLineTotal());
            }

            // Sum credits separately
            if ("CREDIT_GENERATION".equals(item.getCategory()) ||
                "CREDIT_REBATE".equals(item.getCategory())) {
                totalGenerationCredits = totalGenerationCredits.add(item.getLineTotal().abs());
            }

            // Calculate total energy usage
            if (item.getEnergyQuantity() != null && item.getEnergyQuantity().compareTo(BigDecimal.ZERO) > 0) {
                totalUsageKWh = totalUsageKWh.add(item.getEnergyQuantity());
            }
        }

        BigDecimal netPassThrough = totalPassThrough.subtract(totalGenerationCredits);
        BigDecimal passThroughPerKWh = BigDecimal.ZERO;
        BigDecimal netPerKWh = BigDecimal.ZERO;

        if (totalUsageKWh.compareTo(BigDecimal.ZERO) > 0) {
            passThroughPerKWh = totalPassThrough.divide(totalUsageKWh, 6, RoundingMode.HALF_UP);
            netPerKWh = netPassThrough.divide(totalUsageKWh, 6, RoundingMode.HALF_UP);
        }

        return new PassThroughSummary(
                invoiceId,
                totalUsageKWh,
                categoryTotals,
                totalPassThrough,
                totalGenerationCredits,
                netPassThrough,
                passThroughPerKWh,
                netPerKWh
        );
    }

    /**
     * Allocate generation credits to customers based on their usage
     */
    @Transactional(readOnly = true)
    public CreditAllocation allocateGenerationCredits(Long invoiceId, 
                                                      List<CustomerUsage> customers,
                                                      AllocationMethod method) {
        log.debug("Allocating generation credits for invoice {} using method {}", invoiceId, method);

        PassThroughSummary summary = calculatePassThroughCharges(invoiceId);
        BigDecimal totalCredit = summary.totalGenerationCredits();

        if (totalCredit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("No generation credits found for invoice " + invoiceId);
        }

        BigDecimal totalCustomerUsage = customers.stream()
                .map(CustomerUsage::usageKWh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCustomerUsage.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Total customer usage is zero");
        }

        List<CustomerCreditAllocation> allocations = new ArrayList<>();
        BigDecimal creditPerKWh = totalCredit.divide(totalCustomerUsage, 6, RoundingMode.HALF_UP);

        for (CustomerUsage customer : customers) {
            BigDecimal customerCredit = switch (method) {
                case PRO_RATA -> customer.usageKWh()
                        .multiply(creditPerKWh)
                        .setScale(2, RoundingMode.HALF_UP);
                case FIXED -> totalCredit.divide(new BigDecimal(customers.size()), 2, RoundingMode.HALF_UP);
            };

            BigDecimal customerPassThroughCharge = customer.usageKWh()
                    .multiply(summary.passThroughPerKWh())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal netCharge = customerPassThroughCharge.subtract(customerCredit);

            allocations.add(new CustomerCreditAllocation(
                    customer.customerId(),
                    customer.usageKWh(),
                    customerCredit,
                    customerPassThroughCharge,
                    netCharge
            ));
        }

        return new CreditAllocation(
                invoiceId,
                totalCredit,
                totalCustomerUsage,
                creditPerKWh,
                method,
                allocations
        );
    }

    // DTOs
    public record PassThroughSummary(
            Long invoiceId,
            BigDecimal totalUsageKWh,
            Map<String, BigDecimal> categoryTotals,  // Category is now String code
            BigDecimal totalPassThroughCharges,
            BigDecimal totalGenerationCredits,
            BigDecimal netPassThroughAmount,
            BigDecimal passThroughPerKWh,
            BigDecimal netPerKWh
    ) {}

    public record CustomerUsage(
            String customerId,
            BigDecimal usageKWh
    ) {}

    public record CustomerCreditAllocation(
            String customerId,
            BigDecimal usageKWh,
            BigDecimal creditAmount,
            BigDecimal passThroughCharge,
            BigDecimal netCharge
    ) {}

    public record CreditAllocation(
            Long invoiceId,
            BigDecimal totalCredit,
            BigDecimal totalUsageKWh,
            BigDecimal creditPerKWh,
            AllocationMethod method,
            List<CustomerCreditAllocation> allocations
    ) {}

    public enum AllocationMethod {
        PRO_RATA,  // Allocate based on usage percentage
        FIXED      // Equal distribution
    }
}

