# Branding Management System - Complete Implementation

## Overview

A complete tenant branding management system with S3 logo storage, backend proxying, and admin UI for configuration. Admins can upload custom logos, configure brand colors, and customize the display name/tagline for their tenant.

---

## 🎯 Features

✅ **Logo Management**
- Upload main logo (header/sidebar)
- Upload login logo (authentication page)
- Store logos in S3/MinIO bucket (`tenant-logos`)
- Proxy logos through backend for security
- Support for PNG, JPEG, SVG, WebP formats
- 5MB file size limit
- Delete logos via UI

✅ **Branding Configuration**
- Custom display name
- Custom tagline/subtitle
- Primary brand color (hex)
- Secondary brand color (hex)
- Live preview of colors
- Automatic theme application

✅ **Security & Multi-Tenancy**
- Tenant-isolated logo storage
- Authenticated upload endpoints
- Public logo proxy for login page
- S3 object keys stored in database

---

## 📊 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Admin UI (React)                         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ BrandingManagement.tsx                              │  │
│  │ - Logo upload forms                                  │  │
│  │ - Color pickers                                       │  │
│  │ - Preview components                                  │  │
│  └─────────────────────────────────────────────────────┘  │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ brandingApi.ts                                       │  │
│  │ - API calls for upload/update/delete                 │  │
│  │ - URL conversion (S3 key → proxy URL)               │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot)                     │
│                                                             │
│  ┌───────────────────────────────────────────────────┐    │
│  │ AdminBrandingController.java                       │    │
│  │ /api/admin/branding/*                             │    │
│  │ - POST /logo/main (upload)                         │    │
│  │ - POST /logo/login (upload)                        │    │
│  │ - PUT / (update settings)                          │    │
│  │ - DELETE /logo/main                                │    │
│  │ - DELETE /logo/login                               │    │
│  └───────────────────────────────────────────────────┘    │
│                          │                                  │
│  ┌───────────────────────────────────────────────────┐    │
│  │ BrandingController.java (Public)                   │    │
│  │ /api/branding/*                                    │    │
│  │ - GET /{tenantId} (get branding)                   │    │
│  │ - GET /logo/** (proxy logos)                       │    │
│  └───────────────────────────────────────────────────┘    │
│                          │                                  │
│  ┌───────────────────────────────────────────────────┐    │
│  │ S3LogoService.java                                 │    │
│  │ - uploadLogo()                                     │    │
│  │ - retrieveLogo()                                   │    │
│  │ - deleteLogo()                                     │    │
│  └───────────────────────────────────────────────────┘    │
│                          │                                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              S3/MinIO Storage                               │
│                                                             │
│  Bucket: tenant-logos                                       │
│  Structure:                                                 │
│    └── logos/                                               │
│        ├── admin/                                          │
│        │   ├── main-{uuid}.png                            │
│        │   └── login-{uuid}.png                           │
│        ├── tenant2/                                        │
│        │   ├── main-{uuid}.png                            │
│        │   └── login-{uuid}.png                           │
│        └── ...                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema

### Updated `tenants` Table

```sql
ALTER TABLE mcp_invoice.tenants 
  ADD COLUMN logo_url VARCHAR(1000),          -- S3 key: "logos/admin/main-uuid.png"
  ADD COLUMN login_logo_url VARCHAR(1000),    -- S3 key: "logos/admin/login-uuid.png"
  ADD COLUMN primary_color VARCHAR(7),         -- Hex color: "#EE1C25"
  ADD COLUMN secondary_color VARCHAR(7),       -- Hex color: "#00AAFF"
  ADD COLUMN display_name VARCHAR(255),        -- "Acme Energy Portal"
  ADD COLUMN tagline VARCHAR(500);             -- "AI-Powered Invoice Management"
```

**Note:** The `logo_url` and `login_logo_url` fields now store **S3 object keys** (not full URLs). The frontend converts these to proxied URLs automatically.

---

## 🔌 API Endpoints

### Admin Endpoints (Authenticated)

#### 1. Get Current Branding
```
GET /api/admin/branding
Authorization: Bearer {token}
```

**Response:**
```json
{
  "tenantId": "admin",
  "tenantName": "Admin Tenant",
  "displayName": "My Company",
  "tagline": "AI-Powered Invoice Processing",
  "logoUrl": "logos/admin/main-abc123.png",
  "loginLogoUrl": "logos/admin/login-def456.png",
  "primaryColor": "#EE1C25",
  "secondaryColor": "#00AAFF"
}
```

#### 2. Upload Main Logo
```
POST /api/admin/branding/logo/main
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: (binary data)
```

**Response:**
```json
{
  "success": true,
  "message": "Main logo uploaded successfully",
  "s3Key": "logos/admin/main-abc123.png",
  "logoUrl": "/api/branding/logo/logos/admin/main-abc123.png"
}
```

#### 3. Upload Login Logo
```
POST /api/admin/branding/logo/login
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: (binary data)
```

#### 4. Update Branding Settings
```
PUT /api/admin/branding
Authorization: Bearer {token}
Content-Type: application/json

{
  "displayName": "Acme Energy",
  "tagline": "Powering the Future",
  "primaryColor": "#EE1C25",
  "secondaryColor": "#00AAFF"
}
```

#### 5. Delete Main Logo
```
DELETE /api/admin/branding/logo/main
Authorization: Bearer {token}
```

#### 6. Delete Login Logo
```
DELETE /api/admin/branding/logo/login
Authorization: Bearer {token}
```

### Public Endpoints

#### 1. Get Tenant Branding
```
GET /api/branding/{tenantId}
```

Used by login page to load branding before authentication.

#### 2. Proxy Logo (Public)
```
GET /api/branding/logo/logos/admin/main-abc123.png
```

Retrieves logo from S3 and serves it through the backend. Accessible without authentication (needed for login page).

---

## 🎨 Frontend Integration

### Using Branding in Components

The branding is automatically loaded via `BrandingContext` and applied throughout the app:

```typescript
import { useBranding } from './contexts/BrandingContext';
import { getEffectiveLogoUrl, getEffectivePrimaryColor } from './services/brandingApi';

function MyComponent() {
  const { branding } = useBranding();
  
  return (
    <div>
      <img src={getEffectiveLogoUrl(branding)} alt="Logo" />
      <h1 style={{ color: getEffectivePrimaryColor(branding) }}>
        {branding?.displayName}
      </h1>
    </div>
  );
}
```

### Logo URL Conversion

The `brandingApi.ts` automatically converts S3 keys to proxy URLs:

```typescript
// S3 key in database:
"logos/admin/main-abc123.png"

// Converted to proxy URL:
"/api/branding/logo/logos/admin/main-abc123.png"

// Local paths work too:
"/image.png" → "/invoice/image.png"
```

### Theme Colors

Theme colors are dynamically generated from branding:

```typescript
const theme = useMemo(() => createTheme({
  palette: {
    primary: {
      main: branding ? getEffectivePrimaryColor(branding) : '#EE1C25',
    },
    secondary: {
      main: branding ? getEffectiveSecondaryColor(branding) : '#00AAFF',
    },
  },
}), [branding]);
```

---

## 🔒 Security Configuration

### Public Endpoints

The following endpoints are publicly accessible (needed for login page):

```java
.requestMatchers("/api/branding/**").permitAll()
```

### Filter Skip Logic

The `RestApiJwtAuthenticationFilter` skips authentication for branding endpoints:

```java
if (servletPath.startsWith("/api/branding")) {
    log.debug("REST API Filter - Skipping filter for public branding endpoint: {}", servletPath);
    filterChain.doFilter(request, response);
    return;
}
```

---

## 📁 File Structure

### Backend

```
mcp-invoice-server/src/main/java/com/llmocr/mcp/invoice/
├── controller/
│   ├── BrandingController.java              (Public branding API)
│   └── AdminBrandingController.java         (Admin branding management)
├── service/
│   ├── BrandingService.java                 (Branding business logic)
│   └── S3LogoService.java                   (S3 upload/retrieval)
├── domain/
│   └── Tenant.java                          (Updated with branding fields)
└── dto/
    └── BrandingDTO.java                     (Branding data transfer object)
```

### Frontend

```
admin-ui/src/
├── pages/
│   └── BrandingManagement.tsx               (Branding admin UI)
├── contexts/
│   └── BrandingContext.tsx                  (Global branding state)
├── services/
│   └── brandingApi.ts                       (API calls & URL helpers)
└── App.tsx                                   (Updated with branding route)
```

---

## 🚀 Usage Guide

### For Administrators

1. **Navigate to Branding Management**
   - Log in as admin
   - Click "Branding" in the sidebar menu

2. **Upload Logos**
   - **Main Logo**: Click "Choose File" → Select logo (PNG/SVG) → Click "Upload Main Logo"
   - **Login Logo**: Click "Choose File" → Select logo → Click "Upload Login Logo"
   - Logos are automatically stored in S3 and applied immediately

3. **Configure Colors**
   - Click the color pickers for Primary and Secondary colors
   - See live preview below each picker
   - Colors are shown as colored boxes

4. **Set Display Information**
   - Enter **Display Name** (shown in UI header)
   - Enter **Tagline** (shown below logo and on login)

5. **Save Settings**
   - Click "Save Settings"
   - Page will reload to apply new theme colors

### For Developers

**Adding Branding to New Components:**

```typescript
import { useBranding } from '../contexts/BrandingContext';
import { getEffectiveLogoUrl } from '../services/brandingApi';

const MyComponent = () => {
  const { branding } = useBranding();
  
  return (
    <img src={getEffectiveLogoUrl(branding)} alt="Logo" />
  );
};
```

**Getting Effective Values (with fallbacks):**

```typescript
// Display name (falls back to tenant name)
branding ? getEffectiveDisplayName(branding) : 'Default Name'

// Primary color (falls back to #1976d2)
branding ? getEffectivePrimaryColor(branding) : '#1976d2'

// Logo URL (converts S3 keys to proxy URLs)
branding ? getEffectiveLogoUrl(branding) : '/image.png'
```

---

## 🔧 Configuration

### S3 Bucket Setup

The system uses the existing S3 configuration for the tenant. The bucket `tenant-logos` is created automatically on first upload.

**MinIO Setup:**
```bash
# Create bucket (done automatically by S3LogoService)
mc mb local/tenant-logos

# Set public read policy (optional, logos are proxied)
mc anonymous set download local/tenant-logos
```

### File Upload Limits

```java
// In S3LogoService.java
private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
private static final String[] ALLOWED_CONTENT_TYPES = {
    "image/png", "image/jpeg", "image/jpg", "image/svg+xml", "image/webp"
};
```

---

## 🎯 Best Practices

### Logo Specifications

**Main Logo (Header/Sidebar):**
- Recommended size: 400x100px (4:1 ratio)
- Format: PNG or SVG preferred
- Background: Transparent
- Note: Will be inverted to white on colored sidebar background

**Login Logo:**
- Recommended size: 400x400px (square) or 400x200px
- Format: PNG or SVG preferred
- Background: Transparent or white
- Can be more detailed than main logo

### Color Selection

- **Primary Color**: Used for headers, buttons, links (ensure good contrast with white text)
- **Secondary Color**: Used for accents, secondary buttons (complement primary)
- Test colors for WCAG AA compliance
- Recommended: Use your company's brand colors

### Display Name & Tagline

- **Display Name**: Keep concise (max 50 chars recommended)
- **Tagline**: Brief description (max 100 chars recommended)
- Example: "Acme Energy" / "AI-Powered Invoice Management"

---

## 📊 Flow Diagrams

### Logo Upload Flow

```
Admin UI                    Backend                    S3/MinIO
   │                           │                          │
   │ 1. Select file           │                          │
   │ 2. Click Upload          │                          │
   │────────────────────────> │                          │
   │   POST /api/admin/       │                          │
   │   branding/logo/main     │                          │
   │                           │                          │
   │                           │ 3. Validate file        │
   │                           │    (size, type)         │
   │                           │                          │
   │                           │ 4. Generate S3 key      │
   │                           │    logos/admin/         │
   │                           │    main-{uuid}.png      │
   │                           │                          │
   │                           │ 5. Upload to S3         │
   │                           │─────────────────────────>│
   │                           │                          │
   │                           │ 6. Update tenant record │
   │                           │    (store S3 key)       │
   │                           │                          │
   │ 7. Return success         │                          │
   │<──────────────────────── │                          │
   │                           │                          │
   │ 8. Refresh branding      │                          │
   │────────────────────────> │                          │
   │   GET /api/admin/        │                          │
   │   branding               │                          │
   │                           │                          │
   │ 9. Return branding       │                          │
   │<──────────────────────── │                          │
   │    (includes S3 key)     │                          │
   │                           │                          │
   │ 10. Convert S3 key to   │                          │
   │     proxy URL            │                          │
   │     /api/branding/logo/  │                          │
   │     logos/admin/...      │                          │
```

### Logo Display Flow

```
Browser                     Backend                    S3/MinIO
   │                           │                          │
   │ 1. Load app              │                          │
   │────────────────────────> │                          │
   │   GET /api/branding/     │                          │
   │   default                │                          │
   │                           │                          │
   │ 2. Return branding       │                          │
   │<──────────────────────── │                          │
   │    logoUrl: "logos/...   │                          │
   │                           │                          │
   │ 3. Convert to proxy URL  │                          │
   │    /api/branding/logo/   │                          │
   │    logos/admin/main...   │                          │
   │                           │                          │
   │ 4. Request logo          │                          │
   │────────────────────────> │                          │
   │   GET /api/branding/     │                          │
   │   logo/logos/admin/...   │                          │
   │                           │                          │
   │                           │ 5. Retrieve from S3     │
   │                           │─────────────────────────>│
   │                           │                          │
   │                           │ 6. Return file bytes    │
   │                           │<─────────────────────────│
   │                           │                          │
   │ 7. Return logo with      │                          │
   │    content-type header   │                          │
   │<──────────────────────── │                          │
   │                           │                          │
   │ 8. Display logo          │                          │
```

---

## ✅ Testing Checklist

### Upload Testing
- [ ] Upload main logo (PNG)
- [ ] Upload login logo (SVG)
- [ ] Try uploading file > 5MB (should fail)
- [ ] Try uploading non-image file (should fail)
- [ ] Verify logo appears in header
- [ ] Verify logo appears on login page

### Color Testing
- [ ] Change primary color
- [ ] Change secondary color
- [ ] Verify colors preview correctly
- [ ] Save settings and reload
- [ ] Verify theme applies new colors

### Delete Testing
- [ ] Delete main logo
- [ ] Verify fallback to default image
- [ ] Delete login logo
- [ ] Verify fallback to main logo or default

### Multi-Tenancy
- [ ] Upload logo as Tenant A
- [ ] Switch to Tenant B
- [ ] Verify Tenant B doesn't see Tenant A's logo
- [ ] Upload different logo for Tenant B
- [ ] Verify isolation

---

## 🐛 Troubleshooting

### Logos Not Displaying

**Check:**
1. Browser console for 404 errors
2. Network tab: Is `/api/branding/logo/**` returning 200?
3. Backend logs: Any S3 errors?
4. S3 bucket exists: `mc ls local/tenant-logos`

**Solution:**
```bash
# Verify S3 configuration
curl http://localhost:8081/mcp-invoice/api/branding/default

# Check if bucket exists
mc ls local/tenant-logos

# Test logo retrieval
curl http://localhost:8081/mcp-invoice/api/branding/logo/logos/admin/main-xxx.png
```

### Upload Fails

**Check:**
1. File size < 5MB?
2. File type is PNG/JPEG/SVG/WebP?
3. S3 credentials configured?
4. Network error in browser console?

**Solution:**
- Verify S3 config in database: `SELECT * FROM s3_bucket_configuration;`
- Check backend logs for specific error
- Try uploading smaller file

### Colors Not Applying

**Issue:** Colors saved but theme doesn't change

**Solution:**
- Hard refresh browser (Ctrl+Shift+R)
- Clear browser cache
- Ensure you clicked "Save Settings"
- Check if page reloaded after save

---

## 🔜 Future Enhancements

### Planned Features

1. **Logo Cropping Tool**
   - In-browser image cropping
   - Resize/adjust before upload
   - Aspect ratio enforcement

2. **Branding Presets**
   - Save multiple branding configurations
   - Quick switch between presets
   - Industry-specific templates

3. **Advanced Customization**
   - Custom fonts upload
   - Custom CSS injection
   - Favicon customization
   - Email template branding

4. **Multi-Logo Support**
   - Different logos for mobile
   - Dark mode logo variants
   - Favicon in multiple sizes

5. **Branding Analytics**
   - Track logo views
   - A/B testing for branding
   - User preference data

---

## 📝 Summary

The branding management system is now fully operational with:

✅ S3-based logo storage  
✅ Secure backend proxying  
✅ Admin UI for management  
✅ Multi-tenant isolation  
✅ Dynamic theme application  
✅ Public endpoints for login page  
✅ Complete CRUD operations  

Admins can now customize their tenant's appearance through an intuitive UI, with all logos securely stored in S3 and proxied through the backend for security.

