# Vendor Management Feature

## Overview
Implemented a comprehensive vendor management system with database table, MCP tools for AI-powered vendor lookup and creation, and REST API for admin UI management. The system supports multi-tenancy and integrates seamlessly with invoice processing.

## Components Implemented

### 1. Database Schema

**Liquibase Changelog**: `00017-create-vendors-table.xml`

**Table**: `mcp_invoice.vendors`

**Columns**:
- `id` - Primary key (BIGSERIAL)
- `tenant_id` - Multi-tenancy identifier (VARCHAR(100), NOT NULL)
- `vendor_name` - Vendor/supplier name (VARCHAR(500), NOT NULL)
- `vendor_code` - Internal vendor code/ID (VARCHAR(50))
- `tax_id` - Tax ID or EIN (VARCHAR(50))
- `address` - Street address (TEXT)
- `city` - City (VARCHAR(100))
- `state` - State/province (VARCHAR(100))
- `postal_code` - ZIP/postal code (VARCHAR(20))
- `country` - Country (VARCHAR(100))
- `phone` - Phone number (VARCHAR(50))
- `email` - Email address (VARCHAR(255))
- `website` - Website URL (VARCHAR(500))
- `contact_person` - Primary contact name (VARCHAR(200))
- `payment_terms` - Default payment terms (VARCHAR(100))
- `category` - Vendor category (VARCHAR(100))
- `notes` - Additional notes (TEXT)
- `active` - Active status (BOOLEAN, default TRUE)
- `metadata` - Additional flexible data (JSONB)
- `created_at`, `updated_at` - Timestamps
- `created_by`, `updated_by` - Audit fields

**Indexes**:
- `idx_vendors_tenant_id` - Fast tenant filtering
- `idx_vendors_vendor_name` - Name search
- `idx_vendors_vendor_code` - Code lookup
- `idx_vendors_tax_id` - Tax ID lookup
- `idx_vendors_active` - Active status filter
- `idx_vendors_category` - Category filtering
- `idx_vendors_created_at` - Time-based queries

**Constraints**:
- `uk_vendors_tenant_name` - Unique constraint on (tenant_id, vendor_name)

### 2. Domain Entity

**File**: `src/main/java/com/llmocr/mcp/invoice/domain/Vendor.java`

JPA entity with:
- Lombok annotations for getters/setters/builder
- Hibernate annotations for timestamps
- JSONB support for metadata field
- Pre-persist and pre-update hooks

### 3. Repository

**File**: `src/main/java/com/llmocr/mcp/invoice/repository/VendorRepository.java`

**Query Methods**:
- `findByTenantId()` - Get all vendors for tenant
- `findByTenantIdAndId()` - Get specific vendor
- `findByTenantIdAndVendorName()` - Lookup by exact name
- `findByTenantIdAndVendorCode()` - Lookup by code
- `findByTenantIdAndActiveTrue()` - Active vendors only
- `searchByTenantIdAndTerm()` - Full-text search across name, code, tax ID, email
- `findDistinctCategoriesByTenantId()` - Get unique categories
- `existsByTenantIdAndVendorName()` - Check existence
- `countActiveByTenantId()` - Statistics

### 4. MCP Tool Service

**File**: `src/main/java/com/llmocr/mcp/invoice/service/VendorToolService.java`

**Three MCP Tools** for AI/LLM integration:

#### Tool 1: `lookupVendor`
**Description**: Look up vendors by name, vendor code, tax ID, or search term

**Arguments**:
- `vendorName` (optional) - Exact vendor name
- `vendorCode` (optional) - Vendor code
- `taxId` (optional) - Tax ID
- `searchTerm` (optional) - Search across multiple fields
- `limit` (optional, default: 10) - Max results

**Response**:
```json
{
  "found": true,
  "count": 2,
  "vendors": [
    {
      "id": 1,
      "vendorName": "ABC Energy Company",
      "vendorCode": "ABC-001",
      "taxId": "12-3456789",
      "city": "Houston",
      "state": "TX",
      "category": "Energy Provider",
      "active": true
    }
  ]
}
```

#### Tool 2: `createVendor`
**Description**: Create a new vendor in the system

**Required Arguments**:
- `vendorName` - Vendor name (required)

**Optional Arguments**:
- `vendorCode`, `taxId`, `address`, `city`, `state`, `postalCode`, `country`
- `phone`, `email`, `website`, `contactPerson`
- `paymentTerms`, `category`, `notes`, `metadata`

**Response**:
```json
{
  "success": true,
  "message": "Vendor created successfully",
  "vendor": {
    "id": 5,
    "vendorName": "New Energy Corp",
    "vendorCode": "NEW-001",
    "taxId": "98-7654321",
    "active": true,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

#### Tool 3: `updateVendor`
**Description**: Update an existing vendor's information

**Required Arguments**:
- `vendorId` - ID of vendor to update (required)

**Optional Arguments**:
- Any vendor field can be updated

**Response**: Same structure as createVendor

**Integration**: Registered in `McpServerConfiguration.java` alongside invoice tools

### 5. REST API Controller

**File**: `src/main/java/com/llmocr/mcp/invoice/controller/VendorManagementController.java`

**Base Path**: `/api/admin/vendors`

**Endpoints**:

#### GET `/api/admin/vendors`
Get vendors with pagination, search, and filtering

**Query Parameters**:
- `search` - Search term for full-text search
- `category` - Filter by category
- `activeOnly` - Show only active vendors (boolean)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)
- `sortBy` - Sort field (default: vendorName)
- `sortDir` - Sort direction: asc/desc (default: asc)

**Response**:
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

#### GET `/api/admin/vendors/{id}`
Get a specific vendor by ID

**Response**: Full vendor object with all fields

#### POST `/api/admin/vendors`
Create a new vendor

**Request Body**:
```json
{
  "vendorName": "ABC Energy Company",
  "vendorCode": "ABC-001",
  "taxId": "12-3456789",
  "address": "123 Main St",
  "city": "Houston",
  "state": "TX",
  "postalCode": "77001",
  "country": "USA",
  "phone": "(555) 123-4567",
  "email": "billing@abcenergy.com",
  "website": "https://abcenergy.com",
  "contactPerson": "John Smith",
  "paymentTerms": "Net 30",
  "category": "Energy Provider",
  "notes": "Primary electricity supplier"
}
```

**Response**: Created vendor object (HTTP 201)

#### PUT `/api/admin/vendors/{id}`
Update an existing vendor

**Request Body**: Same as POST (all fields optional)

**Response**: Updated vendor object

#### DELETE `/api/admin/vendors/{id}`
Soft-delete a vendor (sets active = false)

**Response**:
```json
{
  "message": "Vendor deactivated successfully"
}
```

#### GET `/api/admin/vendors/categories`
Get unique vendor categories for the tenant

**Response**: Array of category strings

### 6. Security & Tenant Isolation

**Authentication**: Required for all endpoints
- JWT token-based authentication
- User must be assigned to a tenant

**Authorization**:
- Users can only access vendors for their assigned tenant
- Tenant ID automatically enforced in all queries
- Created/updated by audit trail with username

**Data Isolation**:
- All queries filtered by `tenant_id`
- Unique constraint ensures vendor names unique per tenant
- Different tenants can have vendors with same names

## Use Cases

### 1. AI-Powered Invoice Processing
When an LLM processes an invoice:
```
AI: "Found invoice from 'ABC Energy Company'"
System: lookupVendor(vendorName: "ABC Energy Company")
Result: Returns vendor details including tax ID, payment terms
AI: "Validated vendor, processing invoice with payment terms: Net 30"
```

### 2. Auto-Creating Vendors
```
AI: "New vendor detected: 'XYZ Supplies, Inc.'"
System: lookupVendor(vendorName: "XYZ Supplies, Inc.")
Result: Not found
System: createVendor(vendorName: "XYZ Supplies, Inc.", category: "Equipment Supplier")
Result: Vendor created with ID 42
AI: "New vendor registered, continuing invoice processing"
```

### 3. Admin UI Management
- Browse all vendors in paginated table
- Search vendors by name, code, or tax ID
- Filter by category or active status
- Create new vendors manually
- Edit existing vendor information
- Deactivate vendors no longer in use

### 4. Invoice Pre-fill
When creating invoices:
- Lookup vendor by name
- Auto-fill tax ID, address, payment terms
- Consistent vendor information across invoices

## Testing

### Database Migration
```bash
# Run Liquibase migrations
./mvnw liquibase:update

# Verify table created
psql -d your_database -c "\d mcp_invoice.vendors"
```

### MCP Tools (via API)
```bash
# Lookup vendor
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "lookupVendor",
    "arguments": {
      "searchTerm": "Energy"
    }
  }'

# Create vendor
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "createVendor",
    "arguments": {
      "vendorName": "Test Energy Corp",
      "taxId": "12-3456789",
      "category": "Energy Provider"
    }
  }'
```

### REST API
```bash
# Get vendors
curl -X GET "http://localhost:8080/api/admin/vendors?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Create vendor
curl -X POST http://localhost:8080/api/admin/vendors \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "vendorName": "ABC Energy Company",
    "taxId": "12-3456789",
    "category": "Energy Provider"
  }'

# Update vendor
curl -X PUT http://localhost:8080/api/admin/vendors/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "phone": "(555) 999-8888",
    "notes": "Updated contact info"
  }'
```

## Future Enhancements

### Potential Features
1. **Vendor Contracts**: Track contracts and expiration dates
2. **Spend Analytics**: Total spend per vendor
3. **Performance Ratings**: Track vendor performance metrics
4. **Document Management**: Attach W-9s, certificates, etc.
5. **Duplicate Detection**: AI-powered duplicate vendor detection
6. **Vendor Portal**: Self-service portal for vendors
7. **Auto-categorization**: AI categorizes vendors automatically
8. **Approval Workflow**: Multi-step vendor approval process
9. **Integration with Invoices**: Link invoices to vendors (foreign key)
10. **Vendor Hierarchy**: Parent/child vendor relationships

### Integration Improvements
1. **Link invoices to vendors**: Add `vendor_id` foreign key to invoices table
2. **Auto-populate from invoices**: Extract vendor info during OCR
3. **Vendor suggestions**: When processing invoice, suggest matching vendors
4. **Category learning**: AI learns vendor categories from historical data

## Files Created/Modified

### New Files
1. `src/main/java/com/llmocr/mcp/invoice/domain/Vendor.java`
2. `src/main/java/com/llmocr/mcp/invoice/repository/VendorRepository.java`
3. `src/main/java/com/llmocr/mcp/invoice/service/VendorToolService.java`
4. `src/main/java/com/llmocr/mcp/invoice/controller/VendorManagementController.java`
5. `src/main/resources/db/changelog/00017-create-vendors-table.xml`
6. `VENDOR_MANAGEMENT_FEATURE.md` (this file)

### Modified Files
1. `src/main/java/com/llmocr/mcp/invoice/config/McpServerConfiguration.java`
   - Added `VendorToolService` to MCP tool registration

2. `src/main/resources/db/changelog/db.changelog-master.xml`
   - Added include for vendors table changelog

## Benefits

1. **Centralized Vendor Data**: Single source of truth for vendor information
2. **AI Integration**: MCP tools enable AI to lookup and create vendors automatically
3. **Data Quality**: Unique constraints prevent duplicates
4. **Audit Trail**: Track who created/updated vendors and when
5. **Multi-Tenancy**: Complete data isolation between tenants
6. **Scalability**: Indexed for performance with large vendor lists
7. **Flexibility**: JSONB metadata field for custom attributes
8. **Integration Ready**: REST API for UI, MCP tools for AI

## Conclusion

The vendor management system provides a robust foundation for managing supplier/vendor information with:
- ✅ Complete CRUD operations via REST API
- ✅ AI-powered vendor lookup and creation via MCP tools
- ✅ Full multi-tenancy support
- ✅ Comprehensive audit trail
- ✅ Search and filtering capabilities
- ✅ Scalable database design with proper indexing

The system is production-ready and can be immediately used for both AI-powered invoice processing and manual vendor management through the admin UI.



