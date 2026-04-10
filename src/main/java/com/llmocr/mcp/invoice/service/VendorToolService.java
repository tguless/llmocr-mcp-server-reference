package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.Vendor;
import com.llmocr.mcp.invoice.repository.VendorRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for vendor-related MCP tools
 * 
 * Uses Spring AI @Tool annotation with individual parameters (NOT Map<String, Object>)
 * following the same pattern as InvoiceToolService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendorToolService {

    private final VendorRepository vendorRepository;
    private final ObjectMapper objectMapper;

    /**
     * MCP Tool: Lookup vendors by name or search criteria
     */
    @Tool(description = "Look up vendors by name, vendor code, tax ID, or search term. Returns JSON with found status and list of matching vendors.")
    @Transactional(readOnly = true)
    public String lookupVendor(
            @ToolParam(description = "Vendor name to search for (optional)", required = false) String vendorName,
            @ToolParam(description = "Vendor code to search for (optional)", required = false) String vendorCode,
            @ToolParam(description = "Tax ID to search for (optional)", required = false) String taxId,
            @ToolParam(description = "General search term for full-text search (optional)", required = false) String searchTerm) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        
        if (tenantId == null) {
            return createErrorJson("Tenant context not available");
        }

        try {
            List<Vendor> vendors = new ArrayList<>();

            // Search by specific criteria
            if (vendorName != null && !vendorName.trim().isEmpty()) {
                vendorRepository.findByTenantIdAndVendorName(tenantId, vendorName)
                        .ifPresent(vendors::add);
            } else if (vendorCode != null && !vendorCode.trim().isEmpty()) {
                vendorRepository.findByTenantIdAndVendorCode(tenantId, vendorCode)
                        .ifPresent(vendors::add);
            } else if (taxId != null && !taxId.trim().isEmpty()) {
                // Search by tax ID using the search method
                Pageable pageable = PageRequest.of(0, 10);
                Page<Vendor> searchResults = vendorRepository.searchByTenantIdAndTerm(tenantId, taxId, pageable);
                vendors.addAll(searchResults.getContent());
            } else if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Full-text search
                Pageable pageable = PageRequest.of(0, 10, Sort.by("vendorName").ascending());
                Page<Vendor> searchResults = vendorRepository.searchByTenantIdAndTerm(tenantId, searchTerm, pageable);
                vendors.addAll(searchResults.getContent());
            } else {
                // Get all active vendors (limited to 10)
                Pageable pageable = PageRequest.of(0, 10, Sort.by("vendorName").ascending());
                Page<Vendor> allVendors = vendorRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
                vendors.addAll(allVendors.getContent());
            }

            if (vendors.isEmpty()) {
                Map<String, Object> response = Map.of(
                    "found", false,
                    "message", "No vendors found matching the criteria",
                    "vendors", List.of()
                );
                return objectMapper.writeValueAsString(response);
            }

            List<Map<String, Object>> vendorList = vendors.stream()
                    .map(this::toVendorSummary)
                    .collect(Collectors.toList());

            Map<String, Object> response = Map.of(
                "found", true,
                "count", vendors.size(),
                "vendors", vendorList
            );

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error looking up vendor", e);
            return createErrorJson("Error looking up vendor: " + e.getMessage());
        }
    }

    /**
     * MCP Tool: Create a new vendor
     */
    @Tool(description = "Create a new vendor in the system. Requires vendorName. Returns JSON with success status and vendor details including vendorId.")
    @Transactional
    public String createVendor(
            @ToolParam(description = "Vendor name (required)") String vendorName,
            @ToolParam(description = "Vendor code (optional)", required = false) String vendorCode,
            @ToolParam(description = "Tax ID (optional)", required = false) String taxId,
            @ToolParam(description = "Street address (optional)", required = false) String address,
            @ToolParam(description = "City (optional)", required = false) String city,
            @ToolParam(description = "State or province (optional)", required = false) String state,
            @ToolParam(description = "Postal code (optional)", required = false) String postalCode,
            @ToolParam(description = "Country (optional)", required = false) String country,
            @ToolParam(description = "Phone number (optional)", required = false) String phoneNumber,
            @ToolParam(description = "Email address (optional)", required = false) String email,
            @ToolParam(description = "Website URL (optional)", required = false) String website,
            @ToolParam(description = "Contact person name (optional)", required = false) String contactPerson,
            @ToolParam(description = "Payment terms (optional)", required = false) String paymentTerms,
            @ToolParam(description = "Vendor category (optional)", required = false) String category,
            @ToolParam(description = "Additional notes (optional)", required = false) String notes) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        if (tenantId == null) {
            return createErrorJson("Tenant context not available");
        }

        try {
            if (vendorName == null || vendorName.trim().isEmpty()) {
                return createErrorJson("vendorName is required");
            }

            // Check if vendor already exists - if so, return existing vendor details
            Optional<Vendor> existingVendor = vendorRepository.findByTenantIdAndVendorName(tenantId, vendorName);
            if (existingVendor.isPresent()) {
                Vendor vendor = existingVendor.get();
                
                // Build vendor details map (can't use Map.of() - too many fields)
                Map<String, Object> vendorDetails = new HashMap<>();
                vendorDetails.put("vendorId", vendor.getId());
                vendorDetails.put("vendorName", vendor.getVendorName());
                vendorDetails.put("vendorCode", vendor.getVendorCode() != null ? vendor.getVendorCode() : "");
                vendorDetails.put("taxId", vendor.getTaxId() != null ? vendor.getTaxId() : "");
                vendorDetails.put("address", vendor.getAddress() != null ? vendor.getAddress() : "");
                vendorDetails.put("city", vendor.getCity() != null ? vendor.getCity() : "");
                vendorDetails.put("state", vendor.getState() != null ? vendor.getState() : "");
                vendorDetails.put("postalCode", vendor.getPostalCode() != null ? vendor.getPostalCode() : "");
                vendorDetails.put("country", vendor.getCountry() != null ? vendor.getCountry() : "");
                vendorDetails.put("phone", vendor.getPhone() != null ? vendor.getPhone() : "");
                vendorDetails.put("email", vendor.getEmail() != null ? vendor.getEmail() : "");
                vendorDetails.put("website", vendor.getWebsite() != null ? vendor.getWebsite() : "");
                vendorDetails.put("contactPerson", vendor.getContactPerson() != null ? vendor.getContactPerson() : "");
                vendorDetails.put("paymentTerms", vendor.getPaymentTerms() != null ? vendor.getPaymentTerms() : "");
                vendorDetails.put("category", vendor.getCategory() != null ? vendor.getCategory() : "");
                vendorDetails.put("notes", vendor.getNotes() != null ? vendor.getNotes() : "");
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Vendor already exists, returning existing vendor details");
                response.put("alreadyExisted", true);
                response.put("vendor", vendorDetails);
                
                return objectMapper.writeValueAsString(response);
            }

            // Build vendor entity
            Vendor vendor = Vendor.builder()
                    .tenantId(tenantId)
                    .vendorName(vendorName.trim())
                    .vendorCode(vendorCode != null ? vendorCode.trim() : null)
                    .taxId(taxId != null ? taxId.trim() : null)
                    .address(address)
                    .city(city)
                    .state(state)
                    .postalCode(postalCode)
                    .country(country)
                    .phone(phoneNumber)
                    .email(email)
                    .website(website)
                    .contactPerson(contactPerson)
                    .paymentTerms(paymentTerms)
                    .category(category)
                    .notes(notes)
                    .active(true)
                    .createdBy(userId)
                    .build();

            // Save vendor
            Vendor savedVendor = vendorRepository.save(vendor);

            log.info("Created vendor: {} (ID: {}) for tenant: {} by user: {}", 
                    savedVendor.getVendorName(), savedVendor.getId(), tenantId, userId);

            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Vendor created successfully",
                "vendorId", savedVendor.getId(),
                "vendor", toVendorDetail(savedVendor)
            );

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error creating vendor", e);
            return createErrorJson("Error creating vendor: " + e.getMessage());
        }
    }

    /**
     * MCP Tool: Update an existing vendor
     */
    @Tool(description = "Update an existing vendor's information. Requires vendorId. All other fields are optional. Returns JSON with success status and updated vendor details.")
    @Transactional
    public String updateVendor(
            @ToolParam(description = "Vendor ID to update (required)") Long vendorId,
            @ToolParam(description = "New vendor name (optional)", required = false) String vendorName,
            @ToolParam(description = "New vendor code (optional)", required = false) String vendorCode,
            @ToolParam(description = "New tax ID (optional)", required = false) String taxId,
            @ToolParam(description = "New street address (optional)", required = false) String address,
            @ToolParam(description = "New city (optional)", required = false) String city,
            @ToolParam(description = "New state or province (optional)", required = false) String state,
            @ToolParam(description = "New postal code (optional)", required = false) String postalCode,
            @ToolParam(description = "New country (optional)", required = false) String country,
            @ToolParam(description = "New phone number (optional)", required = false) String phoneNumber,
            @ToolParam(description = "New email address (optional)", required = false) String email,
            @ToolParam(description = "New website URL (optional)", required = false) String website,
            @ToolParam(description = "New contact person name (optional)", required = false) String contactPerson,
            @ToolParam(description = "New payment terms (optional)", required = false) String paymentTerms,
            @ToolParam(description = "New vendor category (optional)", required = false) String category,
            @ToolParam(description = "New additional notes (optional)", required = false) String notes) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();
        
        if (tenantId == null) {
            return createErrorJson("Tenant context not available");
        }

        try {
            if (vendorId == null) {
                return createErrorJson("vendorId is required");
            }

            Optional<Vendor> vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, vendorId);
            
            if (vendorOpt.isEmpty()) {
                return createErrorJson("Vendor not found with ID: " + vendorId);
            }

            Vendor vendor = vendorOpt.get();

            // Update fields if provided (not null)
            if (vendorName != null && !vendorName.trim().isEmpty()) {
                vendor.setVendorName(vendorName.trim());
            }
            if (vendorCode != null) {
                vendor.setVendorCode(vendorCode.trim().isEmpty() ? null : vendorCode.trim());
            }
            if (taxId != null) {
                vendor.setTaxId(taxId.trim().isEmpty() ? null : taxId.trim());
            }
            if (address != null) {
                vendor.setAddress(address);
            }
            if (city != null) {
                vendor.setCity(city);
            }
            if (state != null) {
                vendor.setState(state);
            }
            if (postalCode != null) {
                vendor.setPostalCode(postalCode);
            }
            if (country != null) {
                vendor.setCountry(country);
            }
            if (phoneNumber != null) {
                vendor.setPhone(phoneNumber);
            }
            if (email != null) {
                vendor.setEmail(email);
            }
            if (website != null) {
                vendor.setWebsite(website);
            }
            if (contactPerson != null) {
                vendor.setContactPerson(contactPerson);
            }
            if (paymentTerms != null) {
                vendor.setPaymentTerms(paymentTerms);
            }
            if (category != null) {
                vendor.setCategory(category);
            }
            if (notes != null) {
                vendor.setNotes(notes);
            }

            vendor.setUpdatedBy(userId);
            Vendor updatedVendor = vendorRepository.save(vendor);

            log.info("Updated vendor: {} (ID: {}) for tenant: {} by user: {}", 
                    updatedVendor.getVendorName(), updatedVendor.getId(), tenantId, userId);

            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Vendor updated successfully",
                "vendor", toVendorDetail(updatedVendor)
            );

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error updating vendor", e);
            return createErrorJson("Error updating vendor: " + e.getMessage());
        }
    }

    /**
     * Convert Vendor to summary map (for lists)
     */
    private Map<String, Object> toVendorSummary(Vendor vendor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vendor.getId());
        map.put("vendorName", vendor.getVendorName());
        map.put("vendorCode", vendor.getVendorCode());
        map.put("taxId", vendor.getTaxId());
        map.put("city", vendor.getCity());
        map.put("state", vendor.getState());
        map.put("country", vendor.getCountry());
        map.put("email", vendor.getEmail());
        map.put("phone", vendor.getPhone());
        map.put("category", vendor.getCategory());
        map.put("active", vendor.getActive());
        return map;
    }

    /**
     * Convert Vendor to detailed map (for single vendor)
     */
    private Map<String, Object> toVendorDetail(Vendor vendor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vendor.getId());
        map.put("tenantId", vendor.getTenantId());
        map.put("vendorName", vendor.getVendorName());
        map.put("vendorCode", vendor.getVendorCode());
        map.put("taxId", vendor.getTaxId());
        map.put("address", vendor.getAddress());
        map.put("city", vendor.getCity());
        map.put("state", vendor.getState());
        map.put("postalCode", vendor.getPostalCode());
        map.put("country", vendor.getCountry());
        map.put("phone", vendor.getPhone());
        map.put("email", vendor.getEmail());
        map.put("website", vendor.getWebsite());
        map.put("contactPerson", vendor.getContactPerson());
        map.put("paymentTerms", vendor.getPaymentTerms());
        map.put("category", vendor.getCategory());
        map.put("notes", vendor.getNotes());
        map.put("active", vendor.getActive());
        map.put("metadata", vendor.getMetadata());
        map.put("createdAt", vendor.getCreatedAt());
        map.put("updatedAt", vendor.getUpdatedAt());
        map.put("createdBy", vendor.getCreatedBy());
        map.put("updatedBy", vendor.getUpdatedBy());
        return map;
    }

    /**
     * Create error response JSON string
     */
    private String createErrorJson(String message) {
        try {
            Map<String, Object> error = Map.of(
                "success", false,
                "error", message
            );
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            log.error("Error creating error JSON", e);
            return "{\"success\": false, \"error\": \"" + message + "\"}";
        }
    }
}
