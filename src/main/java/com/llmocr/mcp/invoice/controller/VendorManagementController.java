package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.domain.Vendor;
import com.llmocr.mcp.invoice.repository.VendorRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vendor Management Controller for Admin UI
 * 
 * Handles vendor CRUD operations with tenant isolation
 */
@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
@Slf4j
public class VendorManagementController {

    private final VendorRepository vendorRepository;

    /**
     * Get vendors with pagination, search, and filtering
     */
    @GetMapping
    public ResponseEntity<?> getVendors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "vendorName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            // Create pageable with sorting
            Sort sort = sortDir.equalsIgnoreCase("asc") 
                    ? Sort.by(sortBy).ascending() 
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Vendor> vendors;

            // Handle search
            if (search != null && !search.trim().isEmpty()) {
                vendors = vendorRepository.searchByTenantIdAndTerm(tenantId, search, pageable);
            } else if (Boolean.TRUE.equals(activeOnly)) {
                vendors = vendorRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
            } else {
                vendors = vendorRepository.findByTenantId(tenantId, pageable);
            }

            // Filter by category if provided
            if (category != null && !category.trim().isEmpty()) {
                vendors = vendors.map(vendor -> 
                    category.equalsIgnoreCase(vendor.getCategory()) ? vendor : null
                ).map(v -> v);
            }

            // Convert to response DTOs
            List<Map<String, Object>> content = vendors.getContent().stream()
                    .map(this::toVendorResponse)
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", content);
            response.put("totalElements", vendors.getTotalElements());
            response.put("totalPages", vendors.getTotalPages());
            response.put("currentPage", vendors.getNumber());
            response.put("pageSize", vendors.getSize());
            response.put("hasNext", vendors.hasNext());
            response.put("hasPrevious", vendors.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching vendors: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch vendors: " + e.getMessage()));
        }
    }

    /**
     * Get vendor by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getVendorById(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        Optional<Vendor> vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, id);
        
        if (vendorOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Vendor not found"));
        }

        return ResponseEntity.ok(toVendorResponse(vendorOpt.get()));
    }

    /**
     * Create a new vendor
     */
    @PostMapping
    public ResponseEntity<?> createVendor(
            @RequestBody Map<String, Object> vendorData,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            String vendorName = (String) vendorData.get("vendorName");
            
            if (vendorName == null || vendorName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "vendorName is required"));
            }

            // Check if vendor already exists
            if (vendorRepository.existsByTenantIdAndVendorName(tenantId, vendorName)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Vendor with name '" + vendorName + "' already exists"));
            }

            // Build vendor entity
            Vendor vendor = Vendor.builder()
                    .tenantId(tenantId)
                    .vendorName(vendorName)
                    .vendorCode((String) vendorData.get("vendorCode"))
                    .taxId((String) vendorData.get("taxId"))
                    .address((String) vendorData.get("address"))
                    .city((String) vendorData.get("city"))
                    .state((String) vendorData.get("state"))
                    .postalCode((String) vendorData.get("postalCode"))
                    .country((String) vendorData.get("country"))
                    .phone((String) vendorData.get("phone"))
                    .email((String) vendorData.get("email"))
                    .website((String) vendorData.get("website"))
                    .contactPerson((String) vendorData.get("contactPerson"))
                    .paymentTerms((String) vendorData.get("paymentTerms"))
                    .category((String) vendorData.get("category"))
                    .notes((String) vendorData.get("notes"))
                    .active(true)
                    .createdBy(currentUser.getUsername())
                    .build();

            // Handle metadata
            if (vendorData.containsKey("metadata") && vendorData.get("metadata") != null) {
                vendor.setMetadata((Map<String, Object>) vendorData.get("metadata"));
            }

            // Save vendor
            Vendor savedVendor = vendorRepository.save(vendor);

            log.info("Created vendor: {} (ID: {}) for tenant: {} by user: {}", 
                    savedVendor.getVendorName(), savedVendor.getId(), tenantId, currentUser.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED).body(toVendorResponse(savedVendor));

        } catch (Exception e) {
            log.error("Error creating vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error creating vendor: " + e.getMessage()));
        }
    }

    /**
     * Update an existing vendor
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateVendor(
            @PathVariable Long id,
            @RequestBody Map<String, Object> vendorData,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            Optional<Vendor> vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, id);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Vendor not found"));
            }

            Vendor vendor = vendorOpt.get();

            // Update fields if provided
            if (vendorData.containsKey("vendorName")) {
                vendor.setVendorName((String) vendorData.get("vendorName"));
            }
            if (vendorData.containsKey("vendorCode")) {
                vendor.setVendorCode((String) vendorData.get("vendorCode"));
            }
            if (vendorData.containsKey("taxId")) {
                vendor.setTaxId((String) vendorData.get("taxId"));
            }
            if (vendorData.containsKey("address")) {
                vendor.setAddress((String) vendorData.get("address"));
            }
            if (vendorData.containsKey("city")) {
                vendor.setCity((String) vendorData.get("city"));
            }
            if (vendorData.containsKey("state")) {
                vendor.setState((String) vendorData.get("state"));
            }
            if (vendorData.containsKey("postalCode")) {
                vendor.setPostalCode((String) vendorData.get("postalCode"));
            }
            if (vendorData.containsKey("country")) {
                vendor.setCountry((String) vendorData.get("country"));
            }
            if (vendorData.containsKey("phone")) {
                vendor.setPhone((String) vendorData.get("phone"));
            }
            if (vendorData.containsKey("email")) {
                vendor.setEmail((String) vendorData.get("email"));
            }
            if (vendorData.containsKey("website")) {
                vendor.setWebsite((String) vendorData.get("website"));
            }
            if (vendorData.containsKey("contactPerson")) {
                vendor.setContactPerson((String) vendorData.get("contactPerson"));
            }
            if (vendorData.containsKey("paymentTerms")) {
                vendor.setPaymentTerms((String) vendorData.get("paymentTerms"));
            }
            if (vendorData.containsKey("category")) {
                vendor.setCategory((String) vendorData.get("category"));
            }
            if (vendorData.containsKey("notes")) {
                vendor.setNotes((String) vendorData.get("notes"));
            }
            if (vendorData.containsKey("active")) {
                vendor.setActive((Boolean) vendorData.get("active"));
            }
            if (vendorData.containsKey("metadata")) {
                vendor.setMetadata((Map<String, Object>) vendorData.get("metadata"));
            }

            vendor.setUpdatedBy(currentUser.getUsername());
            Vendor updatedVendor = vendorRepository.save(vendor);

            log.info("Updated vendor: {} (ID: {}) for tenant: {} by user: {}", 
                    updatedVendor.getVendorName(), updatedVendor.getId(), tenantId, currentUser.getUsername());

            return ResponseEntity.ok(toVendorResponse(updatedVendor));

        } catch (Exception e) {
            log.error("Error updating vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating vendor: " + e.getMessage()));
        }
    }

    /**
     * Deactivate a vendor (soft delete).
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateVendor(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            Optional<Vendor> vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, id);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Vendor not found"));
            }

            Vendor vendor = vendorOpt.get();

            if (Boolean.FALSE.equals(vendor.getActive())) {
                return ResponseEntity.ok(Map.of("message", "Vendor is already inactive"));
            }

            // Soft deactivate
            vendor.setActive(false);
            vendor.setUpdatedBy(currentUser.getUsername());
            vendorRepository.save(vendor);

            log.info("Deactivated vendor: {} (ID: {}) for tenant: {} by user: {}", 
                    vendor.getVendorName(), vendor.getId(), tenantId, currentUser.getUsername());

            return ResponseEntity.ok(Map.of("message", "Vendor deactivated successfully"));

        } catch (Exception e) {
            log.error("Error deactivating vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deactivating vendor: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete a vendor (hard delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVendor(
            @PathVariable Long id,
            HttpServletRequest request) {

        User currentUser = (User) request.getAttribute("user");

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            Optional<Vendor> vendorOpt = vendorRepository.findByTenantIdAndId(tenantId, id);

            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Vendor not found"));
            }

            Vendor vendor = vendorOpt.get();
            String vendorName = vendor.getVendorName();

            vendorRepository.delete(vendor);
            vendorRepository.flush();

            log.info("Hard deleted vendor: {} (ID: {}) for tenant: {} by user: {}",
                    vendorName, id, tenantId, currentUser.getUsername());

            return ResponseEntity.ok(Map.of("message", "Vendor permanently deleted"));
        } catch (DataIntegrityViolationException e) {
            log.warn("Hard delete blocked for vendor {} in tenant {} due to data integrity constraint",
                    id, tenantId, e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Vendor cannot be hard deleted because it is referenced by invoices or related records. Deactivate it instead."));
        } catch (Exception e) {
            log.error("Error hard deleting vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error hard deleting vendor: " + e.getMessage()));
        }
    }

    /**
     * Get vendor categories for the current tenant
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getVendorCategories(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User must be assigned to a tenant"));
        }

        try {
            List<String> categories = vendorRepository.findDistinctCategoriesByTenantId(tenantId);
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            log.error("Error fetching vendor categories: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch vendor categories"));
        }
    }

    /**
     * Convert Vendor entity to response DTO
     */
    private Map<String, Object> toVendorResponse(Vendor vendor) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", vendor.getId());
        response.put("tenantId", vendor.getTenantId());
        response.put("vendorName", vendor.getVendorName());
        response.put("vendorCode", vendor.getVendorCode());
        response.put("taxId", vendor.getTaxId());
        response.put("address", vendor.getAddress());
        response.put("city", vendor.getCity());
        response.put("state", vendor.getState());
        response.put("postalCode", vendor.getPostalCode());
        response.put("country", vendor.getCountry());
        response.put("phone", vendor.getPhone());
        response.put("email", vendor.getEmail());
        response.put("website", vendor.getWebsite());
        response.put("contactPerson", vendor.getContactPerson());
        response.put("paymentTerms", vendor.getPaymentTerms());
        response.put("category", vendor.getCategory());
        response.put("notes", vendor.getNotes());
        response.put("active", vendor.getActive());
        response.put("metadata", vendor.getMetadata());
        response.put("createdAt", vendor.getCreatedAt());
        response.put("updatedAt", vendor.getUpdatedAt());
        response.put("createdBy", vendor.getCreatedBy());
        response.put("updatedBy", vendor.getUpdatedBy());
        return response;
    }
}



