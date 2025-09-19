package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice Validation Service
 * 
 * Provides validation logic for invoices according to business rules.
 */
@Service
@Slf4j
public class InvoiceValidationService {

    public List<String> validateInvoice(Invoice invoice) {
        List<String> errors = new ArrayList<>();

        // Required field validations
        if (invoice.getTenantId() == null || invoice.getTenantId().trim().isEmpty()) {
            errors.add("Tenant ID is required");
        }

        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            errors.add("Invoice number is required");
        }

        if (invoice.getVendorName() == null || invoice.getVendorName().trim().isEmpty()) {
            errors.add("Vendor name is required");
        }

        if (invoice.getInvoiceDate() == null) {
            errors.add("Invoice date is required");
        }

        if (invoice.getTotalAmount() == null) {
            errors.add("Total amount is required");
        }

        // Business rule validations
        if (invoice.getTotalAmount() != null) {
            if (invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Total amount must be greater than zero");
            }
            
            if (invoice.getTotalAmount().scale() > 2) {
                errors.add("Total amount cannot have more than 2 decimal places");
            }
        }

        if (invoice.getInvoiceDate() != null) {
            LocalDate today = LocalDate.now();
            LocalDate maxPastDate = today.minusYears(10);
            LocalDate maxFutureDate = today.plusYears(1);

            if (invoice.getInvoiceDate().isBefore(maxPastDate)) {
                errors.add("Invoice date cannot be more than 10 years in the past");
            }

            if (invoice.getInvoiceDate().isAfter(maxFutureDate)) {
                errors.add("Invoice date cannot be more than 1 year in the future");
            }
        }

        if (invoice.getDueDate() != null && invoice.getInvoiceDate() != null) {
            if (invoice.getDueDate().isBefore(invoice.getInvoiceDate())) {
                errors.add("Due date cannot be before invoice date");
            }
        }

        // Validate currency code
        if (invoice.getCurrency() != null && !isValidCurrencyCode(invoice.getCurrency())) {
            errors.add("Invalid currency code: " + invoice.getCurrency());
        }

        // Validate amounts consistency
        if (invoice.getSubtotalAmount() != null && invoice.getTaxAmount() != null && invoice.getTotalAmount() != null) {
            BigDecimal calculatedTotal = invoice.getSubtotalAmount().add(invoice.getTaxAmount());
            if (calculatedTotal.compareTo(invoice.getTotalAmount()) != 0) {
                errors.add("Total amount does not match subtotal + tax amount");
            }
        }

        // Validate line items if present
        if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
            BigDecimal lineItemsTotal = invoice.getLineItems().stream()
                    .map(item -> item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (invoice.getSubtotalAmount() != null && lineItemsTotal.compareTo(invoice.getSubtotalAmount()) != 0) {
                errors.add("Subtotal amount does not match sum of line items");
            }
        }

        // Validate string lengths
        if (invoice.getInvoiceNumber() != null && invoice.getInvoiceNumber().length() > 100) {
            errors.add("Invoice number cannot exceed 100 characters");
        }

        if (invoice.getVendorName() != null && invoice.getVendorName().length() > 255) {
            errors.add("Vendor name cannot exceed 255 characters");
        }

        if (invoice.getCustomerName() != null && invoice.getCustomerName().length() > 255) {
            errors.add("Customer name cannot exceed 255 characters");
        }

        if (invoice.getVendorTaxId() != null && invoice.getVendorTaxId().length() > 50) {
            errors.add("Vendor tax ID cannot exceed 50 characters");
        }

        if (invoice.getPaymentTerms() != null && invoice.getPaymentTerms().length() > 100) {
            errors.add("Payment terms cannot exceed 100 characters");
        }

        log.debug("Validation completed for invoice {}. Found {} errors", 
                invoice.getInvoiceNumber(), errors.size());

        return errors;
    }

    private boolean isValidCurrencyCode(String currency) {
        // Basic validation for common currency codes
        if (currency == null || currency.length() != 3) {
            return false;
        }
        
        String[] validCurrencies = {
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "SEK", "NZD",
            "MXN", "SGD", "HKD", "NOK", "KRW", "TRY", "RUB", "INR", "BRL", "ZAR"
        };
        
        for (String validCurrency : validCurrencies) {
            if (validCurrency.equals(currency.toUpperCase())) {
                return true;
            }
        }
        
        return false;
    }
}
