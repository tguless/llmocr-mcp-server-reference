package com.llmocr.mcp.invoice.util;

import com.llmocr.mcp.invoice.domain.InvoiceLineItem.LineItemCategory;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to provide display names and descriptions for line item categories
 */
public class CategoryMetadata {

    private static final Map<LineItemCategory, String> DISPLAY_NAMES = new HashMap<>();
    private static final Map<LineItemCategory, String> DESCRIPTIONS = new HashMap<>();

    static {
        // Pass-Through Categories
        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_ENERGY, "Pass-Through: Energy Generation");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_ENERGY, "Direct energy generation costs passed to customers");

        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_DEMAND, "Pass-Through: Demand Charges");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_DEMAND, "Peak demand charges passed to customers");

        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_FUEL, "Pass-Through: Fuel Adjustment");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_FUEL, "Fuel cost adjustments passed to customers");

        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_TRANSMISSION, "Pass-Through: Transmission");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_TRANSMISSION, "Transmission service costs passed to customers");

        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_ENVIRONMENTAL, "Pass-Through: Environmental");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_ENVIRONMENTAL, "Environmental and regulatory costs passed to customers");

        DISPLAY_NAMES.put(LineItemCategory.PASS_THROUGH_OTHER, "Pass-Through: Other");
        DESCRIPTIONS.put(LineItemCategory.PASS_THROUGH_OTHER, "Other pass-through costs");

        // Credit Categories
        DISPLAY_NAMES.put(LineItemCategory.CREDIT_GENERATION, "Credit: Generation");
        DESCRIPTIONS.put(LineItemCategory.CREDIT_GENERATION, "Credits from energy generation (reduce costs)");

        DISPLAY_NAMES.put(LineItemCategory.CREDIT_REBATE, "Credit: Rebate");
        DESCRIPTIONS.put(LineItemCategory.CREDIT_REBATE, "Rebates and credits reducing customer bills");

        // Internal Categories
        DISPLAY_NAMES.put(LineItemCategory.INTERNAL_ADMIN, "Internal: Administrative");
        DESCRIPTIONS.put(LineItemCategory.INTERNAL_ADMIN, "Internal administrative costs (not passed to customers)");

        DISPLAY_NAMES.put(LineItemCategory.INTERNAL_FEE, "Internal: Fees");
        DESCRIPTIONS.put(LineItemCategory.INTERNAL_FEE, "Internal fees and charges (not passed to customers)");

        DISPLAY_NAMES.put(LineItemCategory.INTERNAL_TAX, "Internal: Taxes");
        DESCRIPTIONS.put(LineItemCategory.INTERNAL_TAX, "Taxes paid by the energy company (not passed to customers)");

        DISPLAY_NAMES.put(LineItemCategory.INTERNAL_OTHER, "Internal: Other");
        DESCRIPTIONS.put(LineItemCategory.INTERNAL_OTHER, "Other internal costs");

        // Review Category
        DISPLAY_NAMES.put(LineItemCategory.REVIEW_REQUIRED, "Requires Review");
        DESCRIPTIONS.put(LineItemCategory.REVIEW_REQUIRED, "Items requiring manual review and categorization");
    }

    public static String getDisplayName(LineItemCategory category) {
        return DISPLAY_NAMES.getOrDefault(category, category.name());
    }

    public static String getDescription(LineItemCategory category) {
        return DESCRIPTIONS.getOrDefault(category, "No description available");
    }
}

