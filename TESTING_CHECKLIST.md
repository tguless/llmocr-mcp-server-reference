# MCP Invoice Server - Testing Checklist

**Line Item Categorization Features**  
**Date:** October 15, 2025

---

## 🎯 Test Objective

Validate that all new line item categorization features work correctly for the third-party billing audit workflow.

---

## ✅ Pre-Testing Setup

### 1. Environment Setup
- [ ] PostgreSQL database running
- [ ] MCP Invoice Server started
- [ ] Health check passes: `curl http://localhost:8081/mcp-invoice/actuator/health`
- [ ] JWT token available for authentication

### 2. Database Verification
```sql
-- Check new tables exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'mcp_invoice' 
AND table_name IN ('line_item_category_rules', 'category_corrections');

-- Check new columns in invoice_line_items
SELECT column_name 
FROM information_schema.columns 
WHERE table_schema = 'mcp_invoice' 
AND table_name = 'invoice_line_items' 
AND column_name IN ('category', 'category_confidence', 'categorized_by', 'requires_review');
```

### 3. MCP Tools Discovery
```bash
curl http://localhost:8081/mcp-invoice/mcp/tools/list | jq '.tools[] | select(.name | contains("LineItem") or contains("Category"))'
```

Expected tools:
- [ ] addInvoiceLineItem
- [ ] categorizeLineItem
- [ ] updateLineItemCategory
- [ ] getLineItemsByCategory
- [ ] calculatePassThroughTotal
- [ ] allocateGenerationCredit
- [ ] getAvailableCategories
- [ ] getCategorizationRules
- [ ] searchCategorizationRules

---

## 📝 Test Cases

### Test Suite 1: Basic Line Item Management

#### Test 1.1: Create Invoice
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/processInvoice \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceNumber": "GA-TEST-001",
    "vendorName": "Georgia Power",
    "totalAmount": "467.82",
    "invoiceDate": "2024-01-15"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns `invoiceId`
- [ ] Invoice created with tenant isolation

#### Test 1.2: Add Energy Line Item
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 1,
    "description": "Energy Charge (1744 kWh x $0.138424)",
    "amount": "241.41",
    "quantity": "1744",
    "unitOfMeasure": "kWh",
    "unitPrice": "0.138424"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns `lineItemId`
- [ ] `suggestedCategory`: "PASS_THROUGH_ENERGY"
- [ ] `confidence`: > 0.90
- [ ] `requiresReview`: false

#### Test 1.3: Add Fuel Line Item
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 2,
    "description": "Fuel Charge (1769 kWh x $0.045055)",
    "amount": "79.70",
    "quantity": "1769",
    "unitOfMeasure": "kWh",
    "unitPrice": "0.045055"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] `suggestedCategory`: "PASS_THROUGH_FUEL"
- [ ] `confidence`: > 0.95

#### Test 1.4: Add Generation Credit (Negative Amount)
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 3,
    "description": "Distributed Generation Credit",
    "amount": "-24291.38",
    "quantity": "1",
    "unitOfMeasure": "credit"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] `suggestedCategory`: "CREDIT_GENERATION"
- [ ] `confidence`: > 0.90

#### Test 1.5: Add Tax Line Item
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 4,
    "description": "Sales Tax",
    "amount": "29.50"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] `suggestedCategory`: "INTERNAL_TAX"
- [ ] `confidence`: > 0.90

---

### Test Suite 2: Categorization Logic

#### Test 2.1: Re-categorize Line Item
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/categorizeLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lineItemId": {LINE_ITEM_ID}
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns category with confidence
- [ ] Alternative categories suggested

#### Test 2.2: Update Category Manually
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/updateLineItemCategory \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lineItemId": {LINE_ITEM_ID},
    "newCategory": "PASS_THROUGH_OTHER",
    "reason": "Testing manual correction"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Category updated to "PASS_THROUGH_OTHER"
- [ ] Correction stored in `category_corrections` table
- [ ] `reviewedBy` field populated

#### Test 2.3: Verify Correction Stored
```sql
SELECT * FROM mcp_invoice.category_corrections 
WHERE line_item_id = {LINE_ITEM_ID};
```

**Expected Result:**
- [ ] Record exists
- [ ] `original_category` and `corrected_category` recorded
- [ ] `correction_reason` stored
- [ ] `corrected_by` is current user

---

### Test Suite 3: Category Discovery

#### Test 3.1: Get All Categories
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/getAvailableCategories \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "includeExamples": true,
    "includeStatistics": true
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns 13 categories
- [ ] Each category has: categoryCode, categoryName, description, keywords, examples
- [ ] Statistics included if `includeStatistics: true`

#### Test 3.2: Get Categorization Rules
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/getCategorizationRules \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "category": "PASS_THROUGH_ENERGY",
    "tenantSpecific": true
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns rules for PASS_THROUGH_ENERGY
- [ ] Each rule has: ruleId, ruleName, priority, matchCriteria, statistics

#### Test 3.3: Search Rules by Keyword
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/searchCategorizationRules \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "energy",
    "minAccuracy": 0.90
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns rules matching "energy"
- [ ] All rules have `accuracyRate >= 0.90`

---

### Test Suite 4: Cost Calculations

#### Test 4.1: Calculate Pass-Through Total
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/calculatePassThroughTotal \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID}
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns `totalUsageKWh`
- [ ] Returns `passThroughCharges` breakdown by category
- [ ] Returns `totalPassThroughCharges`
- [ ] Returns `totalGenerationCredits`
- [ ] Returns `netPassThroughAmount`
- [ ] Returns `passThroughPerKWh` and `netPerKWh`

**Validation:**
```
totalPassThroughCharges = sum of all PASS_THROUGH_* categories
netPassThroughAmount = totalPassThroughCharges - totalGenerationCredits
passThroughPerKWh = totalPassThroughCharges / totalUsageKWh
```

#### Test 4.2: Allocate Generation Credits (Pro-Rata)
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/allocateGenerationCredit \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "allocationMethod": "PRO_RATA",
    "customersJson": "[{\"customerId\":\"CUST-001\",\"usageKWh\":1000},{\"customerId\":\"CUST-002\",\"usageKWh\":500},{\"customerId\":\"CUST-003\",\"usageKWh\":269}]"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns allocations for 3 customers
- [ ] Sum of customer credits = total generation credit
- [ ] CUST-001 gets 2x credit of CUST-002 (2:1 usage ratio)

**Validation:**
```
creditPerKWh = totalCredit / totalCustomerUsage
customer1Credit = customer1Usage * creditPerKWh
sum(all customer credits) = totalCredit
```

#### Test 4.3: Get Line Items by Category
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/getLineItemsByCategory \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "category": "PASS_THROUGH_ENERGY"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Returns only PASS_THROUGH_ENERGY items
- [ ] Each item has complete details

---

### Test Suite 5: Security & Multi-Tenancy

#### Test 5.1: Attempt Access Without Token
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 99,
    "description": "Test",
    "amount": "100"
  }'
```

**Expected Result:**
- [ ] Status: 401 Unauthorized
- [ ] Error message: "Valid Bearer token required"

#### Test 5.2: Attempt Access to Another Tenant's Invoice
```bash
# Use JWT token for tenant B to access tenant A's invoice
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $TENANT_B_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {TENANT_A_INVOICE_ID},
    "lineNumber": 99,
    "description": "Test",
    "amount": "100"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK but with error
- [ ] Error message: "Invoice not found or access denied"

#### Test 5.3: Verify Tenant Isolation in Database
```sql
-- Check line items are properly tenant-isolated
SELECT li.id, i.tenant_id, li.description 
FROM mcp_invoice.invoice_line_items li
JOIN mcp_invoice.invoices i ON li.invoice_id = i.id
WHERE i.tenant_id = 'tenant-a';
```

**Expected Result:**
- [ ] Only sees line items for tenant-a
- [ ] No cross-tenant data leakage

---

### Test Suite 6: Edge Cases

#### Test 6.1: Ambiguous Description
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 10,
    "description": "Miscellaneous Charge",
    "amount": "50.00"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] `suggestedCategory`: "REVIEW_REQUIRED" (or other low-confidence category)
- [ ] `confidence`: < 0.70
- [ ] `requiresReview`: true

#### Test 6.2: Invalid Category Update
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/updateLineItemCategory \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lineItemId": {LINE_ITEM_ID},
    "newCategory": "INVALID_CATEGORY"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK with error
- [ ] Error message: "Invalid category"

#### Test 6.3: Negative Unit Price (Credit)
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 11,
    "description": "Rebate",
    "amount": "-100.00",
    "quantity": "1",
    "unitPrice": "-100.00"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Correctly identified as credit
- [ ] Category: CREDIT_REBATE or CREDIT_GENERATION

#### Test 6.4: Zero Quantity
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {INVOICE_ID},
    "lineNumber": 12,
    "description": "Fixed Charge",
    "amount": "25.00",
    "quantity": "0"
  }'
```

**Expected Result:**
- [ ] Status: 200 OK
- [ ] Handles zero quantity gracefully

---

### Test Suite 7: Performance & Scalability

#### Test 7.1: Add 100 Line Items
```bash
for i in {1..100}; do
  curl -X POST http://localhost:8081/mcp-invoice/tools/addInvoiceLineItem \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"invoiceId\": {INVOICE_ID},
      \"lineNumber\": $i,
      \"description\": \"Energy Charge $i\",
      \"amount\": \"100.00\"
    }"
done
```

**Expected Result:**
- [ ] All 100 items created successfully
- [ ] No performance degradation
- [ ] Response time < 500ms per item

#### Test 7.2: Calculate Pass-Through for Large Invoice
```bash
curl -X POST http://localhost:8081/mcp-invoice/tools/calculatePassThroughTotal \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceId": {LARGE_INVOICE_ID}
  }'
```

**Expected Result:**
- [ ] Calculation completes in < 2 seconds
- [ ] Correct totals despite large dataset

---

### Test Suite 8: Audit Trail

#### Test 8.1: Verify Audit Logs
```sql
SELECT * FROM mcp_invoice.mcp_audit_log 
WHERE tool_name LIKE '%LineItem%' 
ORDER BY executed_at DESC 
LIMIT 10;
```

**Expected Result:**
- [ ] All tool calls logged
- [ ] Includes: user_id, tenant_id, tool_name, success status
- [ ] Timestamps accurate

#### Test 8.2: Check Category Correction Learning
```sql
SELECT 
    original_category,
    corrected_category,
    COUNT(*) as correction_count
FROM mcp_invoice.category_corrections
GROUP BY original_category, corrected_category;
```

**Expected Result:**
- [ ] Corrections recorded
- [ ] Can identify patterns for ML training

---

## 📊 Test Results Summary

### Overall Results

| Test Suite | Total Tests | Passed | Failed | Notes |
|------------|-------------|--------|--------|-------|
| Basic Line Item Management | 5 | | | |
| Categorization Logic | 3 | | | |
| Category Discovery | 3 | | | |
| Cost Calculations | 3 | | | |
| Security & Multi-Tenancy | 3 | | | |
| Edge Cases | 4 | | | |
| Performance & Scalability | 2 | | | |
| Audit Trail | 2 | | | |
| **TOTAL** | **25** | | | |

### Critical Issues Found
1. _[Issue description]_
2. _[Issue description]_

### Minor Issues Found
1. _[Issue description]_
2. _[Issue description]_

---

## 🚀 Deployment Readiness

- [ ] All critical tests pass
- [ ] No security vulnerabilities found
- [ ] Performance meets requirements
- [ ] Documentation complete
- [ ] Approved for production deployment

---

**Tested By:** _________________  
**Date:** _________________  
**Sign-off:** _________________

---

*Testing Checklist Version 1.0*  
*Last Updated: October 15, 2025*

