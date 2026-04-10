# Vendor Foreign Key Integration

**Date**: October 16, 2025  
**Purpose**: Add proper vendor foreign key relationship to invoices to support multiple vendors using the same invoice number

---

## Problem Statement

Previously, the invoice system only had a unique constraint on `(tenant_id, invoice_number)`. This meant:
- ❌ **Two different vendors could NOT use the same invoice number** (e.g., "INV-001")
- ❌ **No referential integrity** between invoices and vendors
- ❌ **Vendor data stored as text** (vendorName) instead of proper foreign key
- ❌ **Duplicate detection was inadequate** - only checked tenant + invoice number

**Real-world scenario**: Georgia Power issues "INV-001" and Alabama Power also issues "INV-001". The second one would be incorrectly rejected as a duplicate!

---

## Solution Overview

Added `vendor_id` as a foreign key column to the `invoices` table and updated the unique constraint to `(tenant_id, vendor_id, invoice_number)`.

### Key Benefits:
✅ **Different vendors can use the same invoice number** (properly scoped)  
✅ **Referential integrity** - invoices must reference valid vendors  
✅ **Better data normalization** - vendor info centralized  
✅ **More accurate duplicate detection** - considers vendor in uniqueness check  
✅ **Enable vendor-level analytics** - easy to query all invoices from a vendor  

---

## Changes Implemented

### 1. Database Schema Changes

**File**: `mcp-invoice-server/src/main/resources/db/changelog/00018-add-vendor-id-to-invoices.xml`

- Added `vendor_id` BIGINT column to `invoices` table
- Created index on `vendor_id` for foreign key performance
- Added foreign key constraint: `fk_invoices_vendor_id` → `vendors.id`
  - `onDelete: RESTRICT` (can't delete vendor with invoices)
  - `onUpdate: CASCADE` (vendor ID changes propagate)
- **Dropped old unique constraint**: `(tenant_id, invoice_number)`
- **Added new unique constraint**: `(tenant_id, vendor_id, invoice_number)`

### 2. Entity Model Updates

**File**: `mcp-invoice-server/src/main/java/com/llmocr/mcp/invoice/domain/Invoice.java`

```java
@Entity
@Table(name = "invoices", schema = "mcp_invoice",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "vendor_id", "invoice_number"}))
public class Invoice {
    
    @Column(name = "vendor_id")
    private Long vendorId;  // NEW: Foreign key to vendors table
    
    // ... existing fields ...
}
```

**Changes**:
- Added `vendorId` field (Long)
- Updated `@UniqueConstraint` annotation to include `vendor_id`

### 3. Repository Layer Updates

**File**: `mcp-invoice-server/src/main/java/com/llmocr/mcp/invoice/repository/InvoiceRepository.java`

**New Methods Added**:

```java
// Primary vendor-aware queries
Optional<Invoice> findByTenantIdAndVendorIdAndInvoiceNumber(String tenantId, Long vendorId, String invoiceNumber);

List<Invoice> findByTenantIdAndVendorId(String tenantId, Long vendorId);

Page<Invoice> findByTenantIdAndVendorId(String tenantId, Long vendorId, Pageable pageable);

boolean existsByTenantIdAndVendorIdAndInvoiceNumber(String tenantId, Long vendorId, String invoiceNumber);

// Enhanced duplicate detection
@Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId " +
       "AND i.vendorId = :vendorId " +
       "AND i.totalAmount = :totalAmount " +
       "AND i.invoiceDate = :invoiceDate")
List<Invoice> findPotentialDuplicatesByVendorId(...);
```

**Purpose**: Support querying invoices by vendor ID with proper tenant isolation.

### 4. MCP Tool Service Updates

**File**: `mcp-invoice-server/src/main/java/com/llmocr/mcp/invoice/service/InvoiceToolService.java`

**Method Signature Change**:

```java
// BEFORE:
public String processInvoice(String invoiceNumber, String vendorName, ...)

// AFTER:
public String processInvoice(Long vendorId, String invoiceNumber, String vendorName, ...)
```

**New Validation Logic**:

1. **Vendor ID is required** - throws error if null with message: "Use lookupVendor or createVendor first."
2. **Vendor existence check** - verifies vendor exists in the same tenant
3. **Updated duplicate check** - now uses `existsByTenantIdAndVendorIdAndInvoiceNumber`
4. **Set vendorId on invoice** - `invoice.setVendorId(vendorId)` when building entity

**Tool Description Updated**:
```
"Process and store a new invoice from extracted data. 
REQUIRED: vendorId must be obtained from lookupVendor or createVendor first. 
Returns the created invoice ID or error details."
```

### 5. AI Agent Prompt Updates

**File**: `src/main/java/com/llmocr/service/PromptTemplateService.java`

**Updated Invoice Processing Workflow** - Now 9 steps (was 8):

```
STEP 1: Extract invoice data (including vendor name)
STEP 2: 🆕 LOOKUP OR CREATE VENDOR (NEW!)
  - Call lookupVendor(vendorName="...")
  - If found: Save vendorId
  - If not found: Call createVendor(...) → Save vendorId
  - 🚨 CRITICAL: MUST have vendorId before proceeding
STEP 3: Discover categorization system
STEP 4: Check if invoice exists
STEP 5: Process invoice with vendorId (UPDATED!)
  - Call process-invoice(vendorId=..., invoiceNumber=..., ...)
  - vendorId is now FIRST parameter and REQUIRED
STEP 6: Add line items
STEP 7: Review categorization
STEP 8: Calculate totals
STEP 9: Confirm completion
```

**Key Instruction Added**:
```
**CRITICAL: vendorId MUST be the first parameter to process-invoice!**
```

**Data Flow Diagram Updated**:
```
STEP 2: lookupVendor(vendorName="ABC Energy")
          ↓
      Returns: {found: true, vendors: [{id: 5, ...}]}
          ↓
      (Save vendorId=5)
          ↓
STEP 5: process-invoice(vendorId=5, invoiceNumber="INV-001", ...)
          ↓
      Returns: {invoiceId: 12345}
```

---

## Database Migration

The Liquibase changelog `00018-add-vendor-id-to-invoices.xml` handles the migration:

1. **Adds `vendor_id` column** (nullable initially for existing data)
2. **Creates index** for foreign key performance
3. **Adds foreign key constraint** to `vendors` table
4. **Drops old constraint** `(tenant_id, invoice_number)`
5. **Adds new constraint** `(tenant_id, vendor_id, invoice_number)`

### Migration Notes:
- ⚠️ **Existing invoices will have `vendor_id = NULL`** until manually populated
- Future enhancement: Add a migration script to populate `vendor_id` from `vendor_name` lookups
- The constraint allows NULL vendor_id for backward compatibility during transition

---

## Testing Checklist

### Manual Testing:

1. **Vendor Lookup & Create**:
   ```
   ✓ Call lookupVendor with "Georgia Power" → returns vendorId=1
   ✓ Call lookupVendor with "Unknown Vendor" → found=false
   ✓ Call createVendor with new vendor → returns vendorId=2
   ```

2. **Invoice Creation with VendorId**:
   ```
   ✓ Create invoice with vendorId=1, invoiceNumber="INV-001" → Success
   ✓ Create invoice with vendorId=2, invoiceNumber="INV-001" → Success (different vendor!)
   ✓ Create invoice with vendorId=1, invoiceNumber="INV-001" again → Error (duplicate!)
   ```

3. **Validation**:
   ```
   ✓ Call processInvoice without vendorId → Error: "Vendor ID is required"
   ✓ Call processInvoice with invalid vendorId → Error: "Vendor not found"
   ✓ Call processInvoice with vendorId from different tenant → Error
   ```

4. **Queries**:
   ```
   ✓ Find all invoices for vendor → findByTenantIdAndVendorId
   ✓ Check invoice exists by vendor → existsByTenantIdAndVendorIdAndInvoiceNumber
   ✓ Get specific invoice → findByTenantIdAndVendorIdAndInvoiceNumber
   ```

5. **End-to-End AI Workflow**:
   ```
   ✓ Extract invoice from PDF
   ✓ AI calls lookupVendor (STEP 2)
   ✓ AI calls process-invoice with vendorId (STEP 5)
   ✓ Invoice created successfully with vendor linkage
   ```

---

## API Changes

### MCP Tool: `processInvoice`

**Old Signature**:
```
processInvoice(invoiceNumber, vendorName, vendorAddress, customerName, 
               invoiceDate, dueDate, totalAmount, currency, description)
```

**New Signature**:
```
processInvoice(vendorId, invoiceNumber, vendorName, vendorAddress, 
               customerName, invoiceDate, dueDate, totalAmount, currency, description)
```

**Breaking Change**: ⚠️ **vendorId is now REQUIRED as first parameter**

### Required Workflow:
```javascript
// 1. Lookup or create vendor first
lookupVendor({ vendorName: "ABC Energy Company" })
// Returns: { found: true, vendors: [{ id: 5, ... }] }

// 2. Then create invoice with vendorId
processInvoice({
  vendorId: 5,  // ← NEW REQUIRED FIELD (from step 1)
  invoiceNumber: "INV-001",
  vendorName: "ABC Energy Company",
  totalAmount: "467.82",
  ...
})
```

---

## Benefits Realized

### 1. **Data Integrity**
- Invoices must reference valid vendors
- Foreign key constraint prevents orphaned data
- Referential integrity enforced at database level

### 2. **Correct Duplicate Detection**
- Invoice numbers are now scoped per vendor
- "Georgia Power INV-001" ≠ "Alabama Power INV-001"
- Prevents false duplicate rejections

### 3. **Better Data Model**
- Normalized vendor information
- Single source of truth for vendor data
- Easier to update vendor info (tax ID, address, etc.)

### 4. **Enhanced Queries**
- Find all invoices from a specific vendor
- Vendor-level analytics and reporting
- Better performance with indexed foreign key

### 5. **Improved Workflow**
- AI agent must lookup/create vendor first
- Ensures vendor exists before invoice creation
- Vendor metadata (payment terms, etc.) available when processing invoice

---

## Future Enhancements

1. **Backfill Existing Data**:
   - Create migration script to populate `vendor_id` from `vendor_name`
   - Match existing invoices to vendors table
   - Handle cases where vendor doesn't exist

2. **Make vendor_id NOT NULL**:
   - After backfill, add NOT NULL constraint
   - Remove legacy `vendor_name` column (keep in vendors table)

3. **Cascade Options**:
   - Consider `onDelete: SET_NULL` for archival scenarios
   - Add soft delete to vendors table instead

4. **Admin UI Updates**:
   - Update Invoice Management page to show vendor from foreign key
   - Add vendor filter dropdown (using vendorId not vendorName)
   - Display vendor details in invoice detail view

5. **Additional Validation**:
   - Validate vendorName matches vendor record
   - Warn if vendor address/taxId mismatch between invoice and vendor table

---

## Deployment Notes

### Prerequisites:
- Vendors table must exist (from changelog 00017)
- Database user must have permission to add foreign keys

### Rollback Plan:
If issues occur, revert by:
1. Drop foreign key: `fk_invoices_vendor_id`
2. Drop new constraint: `uk_invoices_tenant_vendor_invoice_number`
3. Re-add old constraint: `(tenant_id, invoice_number)`
4. Drop `vendor_id` column

### Monitoring:
- Watch for "Vendor ID is required" errors in logs
- Monitor duplicate invoice rejections (should decrease)
- Check foreign key constraint violations

---

## Related Files

- **Database**: `00018-add-vendor-id-to-invoices.xml`
- **Entity**: `Invoice.java`
- **Repository**: `InvoiceRepository.java`
- **Service**: `InvoiceToolService.java`
- **Prompt**: `PromptTemplateService.java` (main app)
- **Vendor Tools**: `VendorToolService.java`

---

## Summary

This integration adds proper vendor foreign key relationships to invoices, enabling:
- ✅ Multiple vendors to use the same invoice number
- ✅ Referential integrity between invoices and vendors
- ✅ Accurate duplicate detection scoped by vendor
- ✅ Better data normalization and vendor management

The change requires updating the AI agent workflow to lookup/create vendors first, then pass `vendorId` when creating invoices.



