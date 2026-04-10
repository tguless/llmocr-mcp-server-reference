# MCP Invoice Server - Admin UI

Material-UI based React admin console for managing categories and viewing transactions.

## 🎯 Features

### 1. **Category Management**
- View all 13 line item categories
- See descriptions, keywords, and examples for each category
- View usage statistics
- Edit category descriptions and keywords (coming soon)
- Search and filter categories

### 2. **Transaction Viewer**
- View all line items with invoice mappings
- Search by invoice number, vendor, or description
- Filter by category and review status
- See categorization confidence scores
- Edit line item categories
- Track items requiring manual review

## 🚀 Getting Started

### Prerequisites
- Node.js 18+ (managed via nvm)
- MCP Invoice Server running on http://localhost:8081

### Installation

```bash
cd /path/to/mcp-invoice-server/admin-ui

# Install dependencies
npm install
```

### Development

```bash
# Start development server
npm start

# Runs on http://localhost:3000
# API calls are proxied to http://localhost:8081/mcp-invoice
```

### Build for Production

```bash
# Build production bundle
npm run build

# Output: ./build directory
```

## 📁 Project Structure

```
admin-ui/
├── src/
│   ├── App.tsx                 # Main app with routing
│   ├── pages/
│   │   ├── CategoryManagement.tsx   # Category management screen
│   │   └── TransactionViewer.tsx    # Transaction viewer screen
│   ├── services/
│   │   └── api.ts              # API service layer
│   ├── types/
│   │   └── (type definitions)
│   └── setupProxy.js           # Dev proxy configuration
├── package.json
└── README.md
```

## 🎨 UI Components

### Navigation
- **Side Drawer**: Category Management, Transaction Viewer
- **Top Bar**: Application title and branding

### Category Management Screen
- **Accordion Cards**: Expandable category details
- **Search**: Filter categories by name, code, or description
- **Category Details**:
  - Description
  - Trigger keywords (chips)
  - Example line items
  - Usage statistics
  - Edit functionality

### Transaction Viewer Screen
- **Data Table**: Sortable, paginated line items
- **Filters**:
  - Search box (ID, invoice #, vendor, description)
  - Category dropdown
  - Review status dropdown
- **Table Columns**:
  - Line Item ID
  - Invoice ID and Number
  - Vendor Name
  - Line Number
  - Description
  - Amount (color-coded for credits)
  - Category (chip with color coding)
  - Confidence score (color-coded)
  - Review status (icon)
  - Actions (edit button)
- **Edit Dialog**: Update category with reason

## 🔌 API Integration

### Endpoints Used

**Category Management:**
- `POST /mcp-invoice/tools/getAvailableCategories` - Get all categories
- `POST /mcp-invoice/tools/getCategorizationRules` - Get rules for categories
- `POST /mcp-invoice/tools/searchCategorizationRules` - Search rules by keyword

**Transaction Viewer:**
- `POST /mcp-invoice/tools/getLineItemsByCategory` - Get line items by category
- `POST /mcp-invoice/tools/updateLineItemCategory` - Update category
- `POST /mcp-invoice/tools/categorizeLineItem` - Re-categorize item

### API Configuration

Edit `.env` to configure API endpoint:

```bash
REACT_APP_API_URL=http://localhost:8081/mcp-invoice
```

## 🎨 Color Coding

### Category Chips
- **Blue (Primary)**: Pass-through charges
- **Green (Success)**: Credits and rebates
- **Gray (Default)**: Internal costs
- **Orange (Warning)**: Requires review

### Confidence Scores
- **Green**: 95-100% (very confident)
- **Blue**: 85-94% (confident)
- **Orange**: 70-84% (moderate)
- **Red**: <70% (low confidence)

### Amounts
- **Green**: Negative amounts (credits)
- **Black**: Positive amounts (charges)

## 🔒 Authentication

Currently uses JWT token from localStorage. Add authentication:

```javascript
// Store token after login
localStorage.setItem('authToken', 'your-jwt-token');

// Remove token on logout
localStorage.removeItem('authToken');
```

API service automatically includes token in Authorization header.

## 📊 Mock Data

Transaction Viewer currently uses mock data for development. Connect to real backend:

1. Ensure MCP Invoice Server is running
2. Add line items to invoices via MCP tools
3. Update `TransactionViewer.tsx` to call real API:

```typescript
const loadTransactions = async () => {
  const result = await transactionAPI.getAllTransactions('tenant-id');
  setTransactions(result);
};
```

## 🛠️ Development Notes

### Adding New Features

**1. New Category Field:**
```typescript
// Update CategoryInfo interface in api.ts
export interface CategoryInfo {
  // ... existing fields
  newField: string;
}

// Update Category Management display
<Typography>{category.newField}</Typography>
```

**2. New Transaction Column:**
```typescript
// Update LineItemTransaction interface
export interface LineItemTransaction {
  // ... existing fields
  newColumn: string;
}

// Add table column
<TableCell>{transaction.newColumn}</TableCell>
```

### Backend API Requirements

For full functionality, implement these backend endpoints:

**1. Get All Transactions**
```java
@GetMapping("/api/line-items")
public List<LineItemDTO> getAllLineItems(@RequestParam String tenantId) {
    // Return line items with invoice details
}
```

**2. Update Category Description** (Future)
```java
@PostMapping("/api/categories/{code}/description")
public void updateCategoryDescription(
    @PathVariable String code,
    @RequestBody String newDescription
) {
    // Update category description in enum documentation
}
```

## 🚀 Deployment

### Option 1: Serve from Backend

Build and copy to Spring Boot static resources:

```bash
npm run build
cp -r build/* ../src/main/resources/static/admin/
```

Access at: `http://localhost:8081/admin/index.html`

### Option 2: Standalone Server

Deploy to nginx, Apache, or CDN:

```bash
npm run build
# Deploy ./build directory
```

Configure API proxy or CORS on backend.

## 🐛 Troubleshooting

**API calls fail:**
- Ensure MCP Invoice Server is running on port 8081
- Check JWT token in localStorage
- Verify CORS configuration on backend

**Categories not loading:**
- Verify `getAvailableCategories` MCP tool is registered
- Check network tab for error responses
- Ensure tenant context is set

**Transactions show mock data:**
- Update `loadMockData()` to call real API
- Implement backend endpoint for line item listing
- Add proper error handling

## 📦 Dependencies

### Core
- React 18
- TypeScript
- Material-UI (MUI) v5
- React Router v6

### Utilities
- Axios (HTTP client)
- http-proxy-middleware (dev proxy)

## 🎯 Future Enhancements

- [ ] Real-time updates with WebSockets
- [ ] Export transactions to CSV/Excel
- [ ] Bulk category updates
- [ ] Analytics dashboard
- [ ] Category rule builder UI
- [ ] Historical categorization accuracy charts
- [ ] Multi-tenant selector
- [ ] Advanced filtering and sorting
- [ ] Audit log viewer

## 📝 License

Same as MCP Invoice Server parent project.

---

**Version:** 1.0.0  
**Last Updated:** October 15, 2025  
**Status:** Development - Ready for testing
