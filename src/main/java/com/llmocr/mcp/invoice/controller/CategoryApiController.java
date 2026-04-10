package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.LineItemCategoryRule;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.LineItemCategoryRuleRepository;
import com.llmocr.mcp.invoice.util.CategoryMetadata;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST API Controller for Category Management (Admin UI)
 * Updated to use database-persisted, tenant-specific categories
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryApiController {

    private final CategoryRepository categoryRepository;
    private final LineItemCategoryRuleRepository categoryRuleRepository;

    /**
     * Get all available categories with their descriptions
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCategories(HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        log.info("🔍 CategoryAPI: Getting categories for tenant: '{}' (attribute keys: {})", 
                tenantId, java.util.Collections.list(request.getAttributeNames()));

        // Fetch categories from database (tenant-specific)
        List<Category> dbCategories = categoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCategoryNameAsc(tenantId);
        
        List<Map<String, Object>> categories = dbCategories.stream()
                .map(category -> {
                    Map<String, Object> categoryInfo = new LinkedHashMap<>();
                    categoryInfo.put("categoryCode", category.getCategoryCode());
                    categoryInfo.put("categoryName", category.getCategoryName());
                    categoryInfo.put("description", category.getDescription());
                    categoryInfo.put("passThrough", category.getIsPassThrough());
                    categoryInfo.put("isCredit", category.getIsCredit());
                    categoryInfo.put("isBillable", category.getIsBillable());
                    categoryInfo.put("required", category.getRequired() != null ? category.getRequired() : false);
                    
                    // Parse keywords from comma-separated string
                    List<String> keywords = category.getKeywords() != null && !category.getKeywords().isEmpty()
                            ? Arrays.stream(category.getKeywords().split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList())
                            : new ArrayList<>();
                    categoryInfo.put("keywords", keywords);
                    
                    // Parse examples from comma-separated string
                    List<String> examples = category.getExamples() != null && !category.getExamples().isEmpty()
                            ? Arrays.stream(category.getExamples().split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList())
                            : new ArrayList<>();
                    categoryInfo.put("examples", examples);
                    
                    // Categorization instructions
                    String instructions = category.getCategorizationInstructions() != null 
                            ? category.getCategorizationInstructions()
                            : "No specific instructions configured";
                    categoryInfo.put("categorizationInstructions", instructions);
                    
                    // Get rule count for this category
                    long ruleCount = categoryRuleRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                            .filter(rule -> rule.getTargetCategory().equals(category.getCategoryCode()))
                            .count();
                    categoryInfo.put("ruleCount", ruleCount);
                    
                    // Statistics (placeholder)
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("totalItemsCategorized", 0);
                    categoryInfo.put("statistics", stats);
                    
                    return categoryInfo;
                })
                .collect(Collectors.toList());

        log.info("Returning {} categories for tenant '{}'", categories.size(), tenantId);
        return ResponseEntity.ok(categories);
    }

    /**
     * Create a new category for the tenant
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(
            @RequestBody Map<String, Object> categoryData,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String createdBy = (String) request.getAttribute("userId");
        
        log.info("REST API: Creating new category for tenant: {}", tenantId);

        try {
            // Validate required fields
            String categoryCode = (String) categoryData.get("categoryCode");
            String categoryName = (String) categoryData.get("categoryName");
            
            if (categoryCode == null || categoryCode.trim().isEmpty()) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Category code is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            if (categoryName == null || categoryName.trim().isEmpty()) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Category name is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Check if category already exists
            if (categoryRepository.existsByTenantIdAndCategoryCode(tenantId, categoryCode)) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Category with code '" + categoryCode + "' already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }
            
            // Create new category
            Category category = Category.builder()
                    .tenantId(tenantId)
                    .categoryCode(categoryCode.toUpperCase().trim())
                    .categoryName(categoryName.trim())
                    .description((String) categoryData.getOrDefault("description", ""))
                    .isPassThrough((Boolean) categoryData.getOrDefault("isPassThrough", false))
                    .isCredit((Boolean) categoryData.getOrDefault("isCredit", false))
                    .isBillable((Boolean) categoryData.getOrDefault("isBillable", false))
                    .keywords((String) categoryData.getOrDefault("keywords", ""))
                    .examples((String) categoryData.getOrDefault("examples", ""))
                    .categorizationInstructions((String) categoryData.getOrDefault("categorizationInstructions", ""))
                    .displayOrder((Integer) categoryData.getOrDefault("displayOrder", 0))
                    .isActive(true)
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
            
            Category savedCategory = categoryRepository.save(category);
            
            log.info("✅ Category '{}' created successfully for tenant '{}'", categoryCode, tenantId);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", "Category created successfully");
            response.put("categoryCode", savedCategory.getCategoryCode());
            response.put("categoryName", savedCategory.getCategoryName());
            response.put("id", savedCategory.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to create category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update category description, keywords, and instructions
     */
    @PutMapping("/{categoryCode}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable String categoryCode,
            @RequestBody Map<String, String> updates,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("REST API: Updating category {} for tenant: {}", categoryCode, tenantId);

        try {
            // Find the category for this tenant
            Optional<Category> categoryOpt = categoryRepository.findByTenantIdAndCategoryCode(tenantId, categoryCode);
            
            if (categoryOpt.isEmpty()) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Category not found");
                errorResponse.put("categoryCode", categoryCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            Category category = categoryOpt.get();
            
            // Update fields if provided
            if (updates.containsKey("description")) {
                category.setDescription(updates.get("description"));
            }
            
            if (updates.containsKey("keywords")) {
                // Convert comma-separated keywords to stored format
                String keywords = updates.get("keywords");
                category.setKeywords(keywords != null ? keywords.trim() : null);
            }
            
            if (updates.containsKey("categorizationInstructions")) {
                category.setCategorizationInstructions(updates.get("categorizationInstructions"));
            }
            
            if (updates.containsKey("examples")) {
                String examples = updates.get("examples");
                category.setExamples(examples != null ? examples.trim() : null);
            }
            
            category.setUpdatedBy(updatedBy);
            
            // Save the updated category
            Category savedCategory = categoryRepository.save(category);
            
            log.info("✅ Category '{}' updated successfully for tenant '{}'", categoryCode, tenantId);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", "Category updated successfully");
            response.put("categoryCode", savedCategory.getCategoryCode());
            response.put("categoryName", savedCategory.getCategoryName());
            response.put("description", savedCategory.getDescription());
            response.put("keywords", savedCategory.getKeywords());
            response.put("examples", savedCategory.getExamples());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error updating category {}: {}", categoryCode, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to update category: " + e.getMessage());
            errorResponse.put("categoryCode", categoryCode);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Delete/deactivate a category
     */
    @DeleteMapping("/{categoryCode}")
    public ResponseEntity<Map<String, Object>> deleteCategory(
            @PathVariable String categoryCode,
            HttpServletRequest request) {
        
        String tenantId = (String) request.getAttribute("tenantId");
        String updatedBy = (String) request.getAttribute("userId");
        
        log.info("REST API: Deleting category {} for tenant: {}", categoryCode, tenantId);

        try {
            // Find the category for this tenant
            Optional<Category> categoryOpt = categoryRepository.findByTenantIdAndCategoryCode(tenantId, categoryCode);
            
            if (categoryOpt.isEmpty()) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Category not found");
                errorResponse.put("categoryCode", categoryCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            Category category = categoryOpt.get();
            
            // Soft delete - set active to false instead of hard delete
            category.setIsActive(false);
            category.setUpdatedBy(updatedBy);
            categoryRepository.save(category);
            
            log.info("✅ Category '{}' deactivated successfully for tenant '{}'", categoryCode, tenantId);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", "Category deleted successfully");
            response.put("categoryCode", categoryCode);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error deleting category {}: {}", categoryCode, e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to delete category: " + e.getMessage());
            errorResponse.put("categoryCode", categoryCode);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Build a human-readable criteria string from a rule
     */
    private String buildRuleCriteria(LineItemCategoryRule rule) {
        StringBuilder criteria = new StringBuilder();
        criteria.append(rule.getRuleName());
        
        List<String> conditions = new ArrayList<>();
        
        if (rule.getKeywordPattern() != null && !rule.getKeywordPattern().isEmpty()) {
            conditions.add("keywords: " + rule.getKeywordPattern());
        }
        
        if (rule.getAmountMin() != null || rule.getAmountMax() != null) {
            if (rule.getAmountMin() != null && rule.getAmountMax() != null) {
                conditions.add("amount: $" + rule.getAmountMin() + " - $" + rule.getAmountMax());
            } else if (rule.getAmountMin() != null) {
                conditions.add("amount: >= $" + rule.getAmountMin());
            } else {
                conditions.add("amount: <= $" + rule.getAmountMax());
            }
        }
        
        if (rule.getIsCredit() != null) {
            conditions.add(rule.getIsCredit() ? "credit only" : "debit only");
        }
        
        if (!conditions.isEmpty()) {
            criteria.append(" (").append(String.join(", ", conditions)).append(")");
        }
        
        return criteria.toString();
    }
}

