package com.llmocr.mcp.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem.LineItemCategory;
import com.llmocr.mcp.invoice.domain.LineItemCategoryRule;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.InvoiceLineItemRepository;
import com.llmocr.mcp.invoice.repository.LineItemCategoryRuleRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * MCP Tools for Category Discovery
 * Allows users to discover available categories and understand categorization rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryDiscoveryToolService {

    private final LineItemCategoryRuleRepository ruleRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Tool: Get all available line item categories with descriptions
     */
    @Tool(description = "Get all available line item categories with descriptions, keywords, and statistics. Helps users understand categorization options.")
    @Transactional(readOnly = true)
    public String getAvailableCategories(
            @ToolParam(description = "Include example line item descriptions (optional)", required = false) Boolean includeExamples,
            @ToolParam(description = "Include usage statistics (optional)", required = false) Boolean includeStatistics) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            log.debug("Getting available categories for tenant {}", tenantId);

            // CRITICAL: Get tenant-specific categories from database, not enum!
            List<Category> dbCategories = categoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCategoryNameAsc(tenantId);

            List<Map<String, Object>> categories = new ArrayList<>();

            for (Category category : dbCategories) {
                Map<String, Object> categoryInfo = new HashMap<>();
                categoryInfo.put("categoryCode", category.getCategoryCode());
                categoryInfo.put("categoryName", category.getCategoryName());
                categoryInfo.put("description", category.getDescription());
                categoryInfo.put("billable", category.getIsBillable());
                categoryInfo.put("passThrough", category.getIsPassThrough());
                categoryInfo.put("isCredit", category.getIsCredit());
                categoryInfo.put("required", category.getRequired() != null ? category.getRequired() : false);
                
                // Parse keywords from comma-separated string
                List<String> keywords = category.getKeywords() != null && !category.getKeywords().isEmpty() 
                    ? Arrays.asList(category.getKeywords().split(",\\s*"))
                    : List.of();
                categoryInfo.put("keywords", keywords);

                if (Boolean.TRUE.equals(includeExamples)) {
                    // For now, use empty list - examples could be added to Category table in future
                    categoryInfo.put("examples", List.of());
                }

                if (Boolean.TRUE.equals(includeStatistics)) {
                    Map<String, Object> stats = new HashMap<>();
                    // Count line items using this category code (now stored as String)
                    long count = lineItemRepository.countByTenantIdAndCategory(tenantId, category.getCategoryCode());
                    stats.put("totalItemsCategorized", count);
                    categoryInfo.put("statistics", stats);
                }

                categories.add(categoryInfo);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("categories", categories);

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalCategories", categories.size());
            summary.put("passThroughCategories", dbCategories.stream().filter(Category::getIsPassThrough).count());
            summary.put("creditCategories", dbCategories.stream().filter(Category::getIsCredit).count());
            summary.put("internalCategories", dbCategories.stream().filter(c -> !c.getIsPassThrough() && !c.getIsCredit()).count());
            summary.put("reviewCategory", dbCategories.stream().filter(c -> "REVIEW_REQUIRED".equals(c.getCategoryCode())).count());
            response.put("summary", summary);

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get available categories: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get available categories: " + e.getMessage(), e);
        }
    }

    /**
     * Tool: Get categorization rules for a specific category
     */
    @Tool(description = "Get categorization rules that determine how line items are categorized. Shows rule priorities, keywords, and accuracy statistics.")
    @Transactional(readOnly = true)
    public String getCategorizationRules(
            @ToolParam(description = "Category name to filter by (optional, null = all categories)", required = false) String category,
            @ToolParam(description = "Include inactive rules (optional, default false)", required = false) Boolean includeInactive,
            @ToolParam(description = "Only show tenant-specific rules (optional, default false)", required = false) Boolean tenantSpecific) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            log.debug("Getting categorization rules for category {} and tenant {}", category, tenantId);

            String targetCategoryCode = null;
            if (category != null && !category.isEmpty()) {
                // Validate category exists for tenant
                Category dbCategory = categoryRepository.findByTenantIdAndCategoryCode(tenantId, category.toUpperCase())
                        .orElse(null);
                if (dbCategory == null) {
                    throw new IllegalArgumentException("Invalid category: " + category);
                }
                targetCategoryCode = category.toUpperCase();
            }

            // Get rules from database
            List<LineItemCategoryRule> rules;
            if (Boolean.TRUE.equals(tenantSpecific)) {
                rules = ruleRepository.findActiveRulesByTenantId(tenantId);
            } else {
                rules = ruleRepository.findAll();
            }

            // Filter by category if specified
            if (targetCategoryCode != null) {
                String finalCategoryCode = targetCategoryCode;
                rules = rules.stream()
                        .filter(r -> r.getTargetCategory() != null && r.getTargetCategory().equals(finalCategoryCode))
                        .toList();
            }

            // Filter by active status
            if (!Boolean.TRUE.equals(includeInactive)) {
                rules = rules.stream()
                        .filter(LineItemCategoryRule::getIsActive)
                        .toList();
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);

            if (targetCategoryCode != null) {
                response.put("category", targetCategoryCode);
                response.put("categoryDescription", "Category: " + targetCategoryCode);
            }

            List<Map<String, Object>> rulesData = rules.stream()
                    .map(this::convertRuleToMap)
                    .toList();

            response.put("rules", rulesData);

            // Add summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRules", rulesData.size());
            summary.put("activeRules", rules.stream().filter(LineItemCategoryRule::getIsActive).count());
            summary.put("tenantSpecificRules", rules.stream().filter(r -> r.getTenantId().equals(tenantId)).count());

            if (!rules.isEmpty()) {
                BigDecimal avgAccuracy = rules.stream()
                        .filter(r -> r.getAccuracyRate() != null)
                        .map(LineItemCategoryRule::getAccuracyRate)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(rules.size()), 4, RoundingMode.HALF_UP);
                summary.put("averageAccuracy", avgAccuracy);
            }

            response.put("summary", summary);

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get categorization rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get categorization rules: " + e.getMessage(), e);
        }
    }

    /**
     * Tool: Search categorization rules by keyword or criteria
     */
    @Tool(description = "Search for categorization rules across all categories. Use keyword to find rules matching specific terms.")
    @Transactional(readOnly = true)
    public String searchCategorizationRules(
            @ToolParam(description = "Keyword to search for in rule names, keywords, or descriptions") String keyword,
            @ToolParam(description = "Minimum accuracy threshold (optional, 0.0-1.0)", required = false) BigDecimal minAccuracy) {
        try {
            String tenantId = McpSecurityContext.getCurrentTenantId();

            if (!McpSecurityContext.isAuthenticated()) {
                throw new SecurityException("Valid Bearer token required");
            }

            List<LineItemCategoryRule> allRules = ruleRepository.findActiveRulesByTenantId(tenantId);

            // Filter by keyword
            if (keyword != null && !keyword.isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();
                allRules = allRules.stream()
                        .filter(r -> r.getRuleName().toLowerCase().contains(lowerKeyword) ||
                                    (r.getKeywordPattern() != null && r.getKeywordPattern().toLowerCase().contains(lowerKeyword)) ||
                                    (r.getDescription() != null && r.getDescription().toLowerCase().contains(lowerKeyword)))
                        .toList();
            }

            // Filter by accuracy
            if (minAccuracy != null) {
                allRules = allRules.stream()
                        .filter(r -> r.getAccuracyRate() != null && r.getAccuracyRate().compareTo(minAccuracy) >= 0)
                        .toList();
            }

            List<Map<String, Object>> results = allRules.stream()
                    .map(rule -> {
                        Map<String, Object> ruleMap = new HashMap<>();
                        ruleMap.put("ruleId", rule.getId());
                        ruleMap.put("ruleName", rule.getRuleName());
                        ruleMap.put("category", rule.getTargetCategory()); // targetCategory is now String
                        ruleMap.put("priority", rule.getPriority());
                        ruleMap.put("accuracyRate", rule.getAccuracyRate());
                        ruleMap.put("matchCount", rule.getMatchCount());
                        ruleMap.put("description", rule.getDescription());
                        return ruleMap;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("searchCriteria", Map.of(
                    "keyword", keyword != null ? keyword : "",
                    "minAccuracy", minAccuracy != null ? minAccuracy : "none"
            ));
            response.put("matchingRules", results);
            response.put("totalMatches", results.size());

            return objectMapper.writeValueAsString(response);

        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search categorization rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search categorization rules: " + e.getMessage(), e);
        }
    }

    // Helper method

    private Map<String, Object> convertRuleToMap(LineItemCategoryRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("ruleId", rule.getId());
        ruleMap.put("ruleName", rule.getRuleName());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("isActive", rule.getIsActive());
        ruleMap.put("description", rule.getDescription());

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("keywordPattern", rule.getKeywordPattern());
        criteria.put("amountMin", rule.getAmountMin());
        criteria.put("amountMax", rule.getAmountMax());
        criteria.put("isCredit", rule.getIsCredit());
        ruleMap.put("matchCriteria", criteria);

        ruleMap.put("targetCategory", rule.getTargetCategory()); // targetCategory is now String

        Map<String, Object> stats = new HashMap<>();
        stats.put("matchCount", rule.getMatchCount());
        stats.put("correctionCount", rule.getCorrectionCount());
        stats.put("accuracyRate", rule.getAccuracyRate());
        ruleMap.put("statistics", stats);

        return ruleMap;
    }
}

