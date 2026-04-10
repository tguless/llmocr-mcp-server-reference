# MCP Invoice Server - Developer Workflow Guide

**For: Third-Party Billing Audit**

---

## 🎯 Quick Start: Processing an Invoice

### Step 1: Create Invoice
```bash
POST /mcp-invoice/tools/processInvoice
{
  "invoiceNumber": "GA-001-2024",
  "vendorName": "Georgia Power",
  "totalAmount": "467.82",
  "invoiceDate": "2024-01-15"
}
→ Returns: invoiceId (e.g., 12345)
```

### Step 2: Add Line Items
```bash
POST /mcp-invoice/tools/addInvoiceLineItem
{
  "invoiceId": 12345,
  "lineNumber": 1,
  "description": "Energy Charge (1744 kWh x $0.138424)",
  "amount": "241.41",
  "quantity": "1744",
  "unitOfMeasure": "kWh",
  "unitPrice": "0.138424"
}
→ Returns: lineItemId + suggestedCategory + confidence
```

**Auto-Categorization Magic:**
- AI analyzes description
- Suggests category (e.g., PASS_THROUGH_ENERGY)
- Provides confidence score (0-1)
- Flags for review if confidence < 85%

### Step 3: Review and Correct (If Needed)
```bash
POST /mcp-invoice/tools/updateLineItemCategory
{
  "lineItemId": 67890,
  "newCategory": "PASS_THROUGH_FUEL",
  "reason": "This is a fuel charge not energy"
}
→ System learns from correction
```

### Step 4: Calculate Pass-Through Total
```bash
POST /mcp-invoice/tools/calculatePassThroughTotal
{
  "invoiceId": 12345
}
→ Returns: breakdown by category + per-kWh rates
```

### Step 5: Allocate Credits to Customers
```bash
POST /mcp-invoice/tools/allocateGenerationCredit
{
  "invoiceId": 12345,
  "allocationMethod": "PRO_RATA",
  "customers": [
    {"customerId": "CUST-001", "usageKWh": 1000},
    {"customerId": "CUST-002", "usageKWh": 500}
  ]
}
→ Returns: credit allocation per customer
```

---

## 🧠 Understanding Categorization

### How It Works

1. **Rule Matching** (Priority 1)
   - Checks tenant-specific rules in database
   - Rules have priorities (higher = checked first)
   - Each rule tracks accuracy rate

2. **AI Heuristics** (Fallback)
   - Keyword pattern matching
   - Amount range checking
   - Credit detection (negative amounts)
   - Context analysis

3. **Confidence Scoring**
   - 0.95-1.00: Very confident (auto-approve)
   - 0.85-0.94: Confident (auto-approve)
   - 0.70-0.84: Moderate (flag for review)
   - < 0.70: Low (requires review)

### Category Decision Tree

```
Is amount negative?
├─ YES → Is it generation-related?
│        ├─ YES → CREDIT_GENERATION (95%)
│        └─ NO  → CREDIT_REBATE (85%)
│
└─ NO  → What keywords are present?
         ├─ "energy", "kwh" → PASS_THROUGH_ENERGY (95%)
         ├─ "fuel" → PASS_THROUGH_FUEL (98%)
         ├─ "demand", "kw" → PASS_THROUGH_DEMAND (95%)
         ├─ "transmission" → PASS_THROUGH_TRANSMISSION (90%)
         ├─ "environmental" → PASS_THROUGH_ENVIRONMENTAL (92%)
         ├─ "tax" → INTERNAL_TAX (95%)
         ├─ "administrative" → INTERNAL_ADMIN (90%)
         └─ No match → REVIEW_REQUIRED (30%)
```

---

## 📊 Category Reference

### Pass-Through Categories (Billable)

| Category | Description | Keywords | Example |
|----------|-------------|----------|---------|
| `PASS_THROUGH_ENERGY` | Energy consumption charges | energy, kwh, kilowatt | Energy Charge (1744 kWh x $0.138424) |
| `PASS_THROUGH_FUEL` | Fuel cost adjustments | fuel, fuel adjustment | Fuel Charge (1769 kWh x $0.045055) |
| `PASS_THROUGH_DEMAND` | Peak demand charges | demand, kw, peak | Demand Charge (250 kW x $12.50) |
| `PASS_THROUGH_TRANSMISSION` | Transmission/delivery fees | transmission, delivery, distribution | Transmission Charge |
| `PASS_THROUGH_ENVIRONMENTAL` | Environmental compliance | environmental, compliance, emission | Environmental Compliance Cost |
| `PASS_THROUGH_OTHER` | Other billable charges | meter, service | Distributed Generation Meter Charge |

### Credit Categories (Reduce Bill)

| Category | Description | Keywords | Example |
|----------|-------------|----------|---------|
| `CREDIT_GENERATION` | Distributed generation credits | generation, solar, renewable | Distributed Generation Credit |
| `CREDIT_REBATE` | Rebates and incentives | rebate, incentive, credit | Merger Transition Credit |

### Internal Categories (Not Billable)

| Category | Description | Keywords | Example |
|----------|-------------|----------|---------|
| `INTERNAL_ADMIN` | Administrative charges | basic service, administrative | Basic Service Charge |
| `INTERNAL_FEE` | Account fees | fee, franchise, late | Municipal Franchise Fee |
| `INTERNAL_TAX` | Taxes | tax, sales tax | Sales Tax |
| `INTERNAL_OTHER` | Misc internal costs | - | Miscellaneous Charge |

### Review Category

| Category | Description | When Used |
|----------|-------------|-----------|
| `REVIEW_REQUIRED` | Manual review needed | Confidence < 70%, ambiguous items |

---

## 🔍 Category Discovery Tools

### Get All Categories
```bash
POST /mcp-invoice/tools/getAvailableCategories
{
  "includeExamples": true,
  "includeStatistics": true
}
```

**Returns:**
- All 13 categories
- Descriptions
- Keywords
- Examples
- Usage statistics

### Get Categorization Rules
```bash
POST /mcp-invoice/tools/getCategorizationRules
{
  "category": "PASS_THROUGH_ENERGY",
  "includeInactive": false,
  "tenantSpecific": true
}
```

**Returns:**
- Rules for specific category
- Priority order
- Match criteria (keywords, amount ranges)
- Accuracy statistics

### Search Rules
```bash
POST /mcp-invoice/tools/searchCategorizationRules
{
  "keyword": "fuel",
  "minAccuracy": 0.90
}
```

**Returns:**
- Rules matching keyword
- Filtered by accuracy threshold

---

## 💡 Common Workflows

### Workflow A: Standard Invoice Processing
```
1. processInvoice → invoiceId
2. Loop: addInvoiceLineItem for each line
   → Auto-categorized
3. Review items with requiresReview=true
4. updateLineItemCategory for corrections
5. calculatePassThroughTotal
6. allocateGenerationCredit to customers
```

### Workflow B: Audit & Verification
```
1. processInvoice (third-party bill)
2. Add line items from third-party PDF
3. calculatePassThroughTotal
4. Compare results with internal records
5. Flag discrepancies
6. Generate audit report
```

### Workflow C: Learning & Improvement
```
1. Review low-confidence items
2. Correct categories via updateLineItemCategory
3. System stores corrections in category_corrections table
4. Future ML training uses corrections
5. Accuracy improves over time
```

---

## 🔒 Security & Multi-Tenancy

All operations are secured:

### JWT Token Required
```bash
Authorization: Bearer <JWT_TOKEN>
```

Token contains:
- `sub`: User ID
- `tenant_id`: Tenant ID (e.g., "acme-energy")

### Automatic Tenant Isolation
```java
// All queries automatically filter by tenant
String tenantId = McpSecurityContext.getCurrentTenantId();
Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId);
```

### Audit Trail
Every operation is logged in `mcp_audit_log`:
- Who performed the action
- What tool was called
- When it happened
- Success/failure status

---

## 🐛 Troubleshooting

### Problem: Low Categorization Confidence
**Solution:**
1. Check if keywords match category keywords
2. Review description for ambiguity
3. Add tenant-specific rule for similar items
4. Manually categorize and system will learn

### Problem: Wrong Category Assigned
**Solution:**
```bash
# Correct the category
POST /mcp-invoice/tools/updateLineItemCategory
{
  "lineItemId": 123,
  "newCategory": "CORRECT_CATEGORY",
  "reason": "This is actually a transmission charge"
}
# System learns and improves
```

### Problem: Pass-Through Total Seems Wrong
**Solution:**
1. Get line items by category to verify
```bash
POST /mcp-invoice/tools/getLineItemsByCategory
{
  "invoiceId": 123,
  "category": "PASS_THROUGH_ENERGY"
}
```
2. Check for uncategorized items (category = null)
3. Verify credits are correctly categorized

### Problem: Credit Allocation Doesn't Match Total
**Solution:**
- Ensure customer usage totals match invoice usage
- Verify generation credits are negative amounts
- Check allocation method (PRO_RATA vs FIXED)

---

## 📈 Performance Tips

1. **Batch Operations**: Add multiple line items before calculating totals
2. **Rule Optimization**: High-accuracy rules should have higher priority
3. **Caching**: Category definitions are cached in memory
4. **Indexing**: Category queries use database indexes

---

## 🧪 Testing with Sample Data

### Sample Georgia Power Invoice

**Line Items:**
```
1. Energy Charge (1744 kWh x $0.138424) = $241.41
2. Fuel Charge (1769 kWh x $0.045055) = $79.70
3. Environmental Compliance Cost = $36.71
4. Demand Charge (250 kW x $12.50) = $3,125.00
5. Distributed Generation Credit = -$24,291.38
6. Basic Service Charge = $18.00
7. Sales Tax = $29.50
```

**Expected Categorization:**
```
1 → PASS_THROUGH_ENERGY (98%)
2 → PASS_THROUGH_FUEL (98%)
3 → PASS_THROUGH_ENVIRONMENTAL (95%)
4 → PASS_THROUGH_DEMAND (95%)
5 → CREDIT_GENERATION (95%)
6 → INTERNAL_ADMIN (90%)
7 → INTERNAL_TAX (95%)
```

**Expected Pass-Through Total:**
```
Energy:         $241.41
Fuel:           $79.70
Environmental:  $36.71
Demand:         $3,125.00
-----------------
Subtotal:       $3,482.82
Credits:        -$24,291.38
-----------------
Net:            -$20,808.56
```

---

## 📚 Additional Resources

- **API Documentation**: `/mcp-invoice-server/README.md`

---

*Last Updated: October 15, 2025*

