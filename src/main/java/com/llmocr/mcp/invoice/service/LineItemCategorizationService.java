package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.Category;
import com.llmocr.mcp.invoice.domain.CategoryCorrection;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem;
import com.llmocr.mcp.invoice.domain.InvoiceLineItem.CategorizationMethod;
import com.llmocr.mcp.invoice.domain.LineItemCategoryRule;
import com.llmocr.mcp.invoice.repository.CategoryCorrectionRepository;
import com.llmocr.mcp.invoice.repository.CategoryRepository;
import com.llmocr.mcp.invoice.repository.LineItemCategoryRuleRepository;
import com.llmocr.mcp.invoice.security.McpSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for categorizing invoice line items.
 * 
 * Relies on tenant-specific rules and AI-based categorization.
 * Removed hardcoded heuristics - AI should query available categories and decide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineItemCategorizationService {

    private final LineItemCategoryRuleRepository ruleRepository;
    private final CategoryCorrectionRepository correctionRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Categorization result with confidence score
     * Category is now String code to support tenant-specific custom categories
     */
    public record CategorizationResult(
            String category,
            BigDecimal confidence,
            CategorizationMethod method,
            String reasoning,
            Map<String, BigDecimal> alternatives
    ) {}

    /**
     * Categorize a line item using tenant-specific rules.
     * Falls back to REVIEW_REQUIRED to let AI handle categorization via MCP tools.
     */
    @Transactional(readOnly = true)
    public CategorizationResult categorizeLineItem(InvoiceLineItem lineItem) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String description = lineItem.getDescription().toLowerCase();
        BigDecimal amount = lineItem.getLineTotal();
        boolean isCredit = amount.compareTo(BigDecimal.ZERO) < 0;

        log.debug("Categorizing line item: {} (amount: {})", description, amount);

        // Try tenant-specific rules first (if configured)
        List<LineItemCategoryRule> rules = ruleRepository.findActiveRulesByTenantId(tenantId);
        for (LineItemCategoryRule rule : rules) {
            if (matchesRule(rule, description, amount, isCredit)) {
                rule.incrementMatchCount();
                ruleRepository.save(rule);
                
                return new CategorizationResult(
                        rule.getTargetCategory(),
                        rule.getAccuracyRate() != null ? rule.getAccuracyRate() : new BigDecimal("0.95"),
                        CategorizationMethod.RULE,
                        "Matched tenant rule: " + rule.getRuleName(),
                        Map.of()
                );
            }
        }

        // No rules matched - return null to force explicit categorization
        // CRITICAL: Do NOT hardcode categories - they must be validated against tenant's available categories
        log.debug("No rules matched for line item, leaving category null for manual assignment: {}", description);
        return new CategorizationResult(
                null,  // No category assigned - requires manual categorization via MCP tools
                new BigDecimal("0.00"),
                CategorizationMethod.AI,
                "No matching rules - category must be explicitly set using tenant's available categories",
                Map.of()
        );
    }

    /**
     * Check if a line item matches a categorization rule
     */
    private boolean matchesRule(LineItemCategoryRule rule, String description, 
                                BigDecimal amount, boolean isCredit) {
        // Check keyword pattern
        if (rule.getKeywordPattern() != null && !rule.getKeywordPattern().isEmpty()) {
            String[] keywords = rule.getKeywordPattern().toLowerCase().split(",");
            boolean keywordMatch = false;
            for (String keyword : keywords) {
                if (description.contains(keyword.trim())) {
                    keywordMatch = true;
                    break;
                }
            }
            if (!keywordMatch) return false;
        }

        // Check amount range
        if (rule.getAmountMin() != null && amount.compareTo(rule.getAmountMin()) < 0) {
            return false;
        }
        if (rule.getAmountMax() != null && amount.compareTo(rule.getAmountMax()) > 0) {
            return false;
        }

        // Check credit flag
        if (rule.getIsCredit() != null && rule.getIsCredit() != isCredit) {
            return false;
        }

        return true;
    }


    /**
     * Learn from user corrections
     */
    @Transactional
    public void learnFromCorrection(Long lineItemId, 
                                   String originalCategory,
                                   BigDecimal originalConfidence,
                                   String correctedCategory,
                                   String correctionReason,
                                   String lineItemDescription,
                                   BigDecimal lineItemAmount) {
        String tenantId = McpSecurityContext.getCurrentTenantId();
        String userId = McpSecurityContext.getCurrentUserId();

        // Store correction
        CategoryCorrection correction = CategoryCorrection.builder()
                .tenantId(tenantId)
                .lineItemId(lineItemId)
                .originalCategory(originalCategory)
                .originalConfidence(originalConfidence)
                .correctedCategory(correctedCategory)
                .correctionReason(correctionReason)
                .lineItemDescription(lineItemDescription)
                .lineItemAmount(lineItemAmount)
                .correctedBy(userId)
                .build();

        correctionRepository.save(correction);

        log.info("Stored correction for line item {}: {} -> {} by {}", 
                lineItemId, originalCategory, correctedCategory, userId);

        // TODO: Trigger ML model retraining (future enhancement)
    }
}

