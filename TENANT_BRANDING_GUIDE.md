# Tenant Branding System - Complete Implementation Guide

## Overview

The application now supports full tenant-specific branding customization. Administrators can configure logos, colors, and display names on a per-tenant basis.

---

## 🎨 Branding Fields

Each tenant can now customize:

1. **Logo URL** - Main application header logo
2. **Login Logo URL** - Login page logo (defaults to main logo if not set)
3. **Primary Color** - Main brand color (hex format: #RRGGBB)
4. **Secondary Color** - Accent brand color (hex format: #RRGGBB)
5. **Display Name** - Custom company/system name
6. **Tagline** - Subtitle shown below logo and on login page

---

## 🖼️ Image Generation Prompts

### 1. Main Application Logo (Top-Left Header)

**Recommended Size:** 400x100px (4:1 ratio)  
**Format:** PNG with transparency  
**Background:** Transparent

**AI Image Generation Prompt:**

```
Create a modern, professional company logo for an invoice management system.
The logo should be:
- Horizontal orientation (4:1 ratio, 400x100 pixels)
- Clean and minimal design
- Professional business aesthetic
- Transparent background (PNG format)
- High contrast for visibility on both light and dark backgrounds
- Suitable for a SaaS application header
- Can be inverted to white for use on colored backgrounds

Style: Modern, corporate, tech-forward
Colors: Use the company's brand colors (can be customized)
Text: Company name in a clean, sans-serif font
Icon: Abstract symbol representing documents, invoices, or data processing
```

**Alternative Prompt (Energy Sector):**

```
Design a corporate logo for an energy company's invoice management platform.
The logo should feature:
- Horizontal layout (400x100px)
- Energy/power industry symbolism (lightning bolt, circuit, or abstract energy wave)
- Corporate red and blue color scheme
- Modern, bold typography
- Transparent background
- Professional and trustworthy appearance
- Recognizable at small sizes

The logo will be used in web application headers and should work well when inverted to white.
```

---

### 2. Login Page Logo

**Recommended Size:** 400x200px (2:1 ratio) or square 400x400px  
**Format:** PNG with transparency  
**Background:** Transparent or white

**AI Image Generation Prompt:**

```
Create a full-color logo for a login/authentication page of an invoice management system.

Requirements:
- Square or slightly rectangular orientation (400x400px or 400x200px)
- Full color version (not designed to be inverted)
- Transparent or white background
- Larger and more detailed than the header logo
- Professional, trustworthy appearance
- Modern SaaS application aesthetic
- Should inspire confidence in users signing in

Include:
- Company name prominently displayed
- Tagline or subtitle (e.g., "Invoice Management System" or "AI-Powered Document Processing")
- Icon or symbol representing the service
- Clean, readable typography
- Brand colors that convey professionalism

Style: Corporate, modern, welcoming
Use case: Displayed on login/authentication pages
```

**Alternative Prompt (Customized for Energy Sector):**

```
Design a login page logo for an energy company's invoice processing platform.

Specifications:
- 400x400px square or 400x200px rectangular
- Full-color corporate branding
- Include company name and tagline "Invoice Management System"
- Energy industry visual elements (abstract waves, circuits, or power symbols)
- Professional color palette: primary red (#EE1C25) and accent blue (#00AAFF)
- Modern, bold sans-serif typography
- Icon incorporating document/invoice imagery with energy motifs
- White or transparent background
- High resolution for crisp display on authentication screens

The logo should convey: Security, efficiency, modern technology, and corporate reliability
```

---

## 📊 Database Schema Changes

### New Columns Added to `tenants` Table:

```sql
ALTER TABLE mcp_invoice.tenants ADD COLUMN logo_url VARCHAR(1000);
ALTER TABLE mcp_invoice.tenants ADD COLUMN login_logo_url VARCHAR(1000);
ALTER TABLE mcp_invoice.tenants ADD COLUMN primary_color VARCHAR(7);
ALTER TABLE mcp_invoice.tenants ADD COLUMN secondary_color VARCHAR(7);
ALTER TABLE mcp_invoice.tenants ADD COLUMN display_name VARCHAR(255);
ALTER TABLE mcp_invoice.tenants ADD COLUMN tagline VARCHAR(500);
```

---

## 🔧 Backend Implementation

### 1. New Files Created:

- **`BrandingDTO.java`** - Data transfer object for branding information
- **`BrandingService.java`** - Service layer for branding operations
- **`BrandingController.java`** - REST endpoints for branding data

### 2. API Endpoints:

#### Get Branding by Tenant ID (Public)
```
GET /api/branding/{tenantId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "tenantId": "admin",
    "tenantName": "Admin Tenant",
    "displayName": "My Company",
    "tagline": "AI-Powered Invoice Processing",
    "logoUrl": "/path/to/logo.png",
    "loginLogoUrl": "/path/to/login-logo.png",
    "primaryColor": "#EE1C25",
    "secondaryColor": "#00AAFF"
  }
}
```

#### Get Default Branding (Public)
```
GET /api/branding/default
```

Returns branding for the admin tenant or system defaults.

---

## 🎨 Frontend Implementation

### 1. New Files Created:

- **`brandingApi.ts`** - API service for fetching branding data
- **`BrandingContext.tsx`** - React context for global branding state

### 2. Updated Components:

- **`App.tsx`** - Uses branding for theme colors and logos
- **`Login.tsx`** - Uses branding for login page customization
- **`index.tsx`** - Wrapped with `BrandingProvider`

### 3. How Branding is Applied:

1. **Theme Colors**: Material-UI theme is dynamically created based on tenant's primary/secondary colors
2. **Logos**: Header and login logos are fetched from tenant branding URLs
3. **Display Names**: Company name and tagline are displayed from branding data
4. **Fallbacks**: If branding is not set, defaults to the default tenant branding

---

## 🚀 How to Configure Tenant Branding

### Option 1: Direct Database Update

```sql
UPDATE mcp_invoice.tenants
SET 
  logo_url = 'https://your-cdn.com/logo.png',
  login_logo_url = 'https://your-cdn.com/login-logo.png',
  primary_color = '#EE1C25',
  secondary_color = '#00AAFF',
  display_name = 'Your Company Name',
  tagline = 'Your Company Tagline'
WHERE tenant_id = 'admin';
```

### Option 2: Future Admin UI (To Be Implemented)

A tenant settings page can be created where admins can:
- Upload logos
- Choose colors with a color picker
- Set display name and tagline
- Preview changes before saving

---

## 🎯 Branding Best Practices

### Logo Requirements:

1. **Main Logo (Header)**:
   - Simple, recognizable design
   - Works well at small sizes (40px height)
   - Can be inverted to white (will be shown on colored background)
   - Transparent background recommended

2. **Login Logo**:
   - More detailed, full-color version allowed
   - Larger size (60px height on login page)
   - Should include company name for clarity
   - Transparent or white background

### Color Guidelines:

1. **Primary Color**:
   - Used for headers, buttons, links
   - Should have good contrast with white text
   - Represents your main brand color

2. **Secondary Color**:
   - Used for accents, secondary buttons
   - Complements primary color
   - Can be lighter or darker variation

### Text Guidelines:

1. **Display Name**:
   - Clear, concise company/system name
   - Max 50 characters recommended
   - Example: "Acme Energy Portal"

2. **Tagline**:
   - Brief description of the system
   - Max 100 characters recommended
   - Example: "AI-Powered Invoice Management"

---

## 🔍 Testing

After configuring branding:

1. **Restart the application** to apply database changes
2. **Check the login page** - Should show custom logo, colors, and tagline
3. **Log in and check header** - Main logo should appear in sidebar
4. **Verify theme colors** - Buttons and highlights should use your colors
5. **Test mobile view** - Logo should appear in mobile header

---

## 🎨 Color Examples

### Professional Blue Theme:
```
Primary: #1976d2 (Blue)
Secondary: #42a5f5 (Light Blue)
```

### Energy Sector (Default):
```
Primary: #EE1C25 (Red)
Secondary: #00AAFF (Blue)
```

### Tech/SaaS Green:
```
Primary: #4CAF50 (Green)
Secondary: #81C784 (Light Green)
```

### Corporate Purple:
```
Primary: #6200EA (Deep Purple)
Secondary: #B388FF (Light Purple)
```

---

## 📝 Notes

1. **Logo Hosting**: Logos should be hosted on a CDN or public URL. Consider using S3/MinIO with public read access.

2. **Default Behavior**: If no branding is configured, the system falls back to:
   - Logo: `/image.png` (existing default logo)
   - Primary Color: `#1976d2` (Material-UI default blue)
   - Secondary Color: `#42a5f5` (Light blue)
   - Display Name: "Invoice Management System"
   - Tagline: "AI-Powered Document Processing"

3. **Security**: The branding endpoint (`/api/branding/*`) is public because it needs to be accessible on the login page before authentication.

4. **Caching**: Consider implementing browser caching for branding data to improve performance.

5. **Multi-Tenancy**: In the future, branding could be determined by:
   - Subdomain (e.g., `acme.yoursystem.com` loads Acme branding)
   - Custom domain mapping
   - User's tenant association after login

---

## 🔜 Future Enhancements

1. **Admin UI for Branding Management**:
   - Upload logos directly through UI
   - Color picker for brand colors
   - Live preview of branding changes
   - Logo cropping/resizing tools

2. **Advanced Branding**:
   - Custom CSS injection
   - Font family customization
   - Background patterns
   - Favicon customization
   - Email template branding

3. **Multi-Tenant Detection**:
   - Automatic branding based on subdomain
   - Custom domain support
   - Tenant detection by URL path

4. **Branding Assets CDN**:
   - Integrated asset management
   - Automatic image optimization
   - Version control for branding assets

---

## ✅ Implementation Complete

The tenant branding system is now fully functional and ready for use. Administrators can customize the appearance of the application on a per-tenant basis through database configuration, with a future admin UI planned for easier management.

