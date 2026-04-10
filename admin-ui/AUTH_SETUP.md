# Authentication Setup Guide

**Issue:** Getting 401 errors when accessing the admin UI  
**Cause:** MCP Invoice Server requires JWT authentication  
**Solution:** Configure authentication token or disable auth for development

---

## ✅ Quick Fix - Option 1: Configure Auth Token in UI

1. **Start the admin UI:**
   ```bash
   cd /path/to/mcp-invoice-server/admin-ui
   ./start.sh
   ```

2. **Navigate to Settings:**
   - Open http://localhost:3000
   - Click **"Settings"** in the sidebar (⚙️ icon)

3. **Add your JWT token:**
   - Paste your JWT token in the text field
   - Click **"Save Token"**
   - Done! API calls will now work

---

## 🔑 How to Get a JWT Token

### From the Main LLM-OCR Application:

1. **Login to LLM-OCR:**
   - Open http://localhost:8080 (main application)
   - Login with your credentials

2. **Get token from browser:**
   ```
   Open Developer Tools (F12) → Console
   Run: localStorage.getItem('authToken')
   Copy the token (long string starting with "eyJ...")
   ```

3. **Use in Admin UI:**
   - Paste into Settings page
   - Save

---

## 🛠️ Option 2: Disable Auth for Development

If you're developing locally and don't need authentication, you can temporarily disable it:

### Backend Configuration:

**File:** `mcp-invoice-server/src/main/resources/application.yml`

Add:
```yaml
spring:
  security:
    enabled: false  # Disable for development only!
```

**OR** create a test profile:

**File:** `mcp-invoice-server/src/main/resources/application-dev.yml`

```yaml
spring:
  security:
    enabled: false
```

Then run with:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

⚠️ **Warning:** Only disable authentication in local development environments!

---

## 🔐 Option 3: Create a Test Token

If you need a token for testing but don't want to login:

### Generate a Test JWT:

**Create:** `mcp-invoice-server/src/test/java/com/llmocr/mcp/invoice/util/JwtTestUtil.java`

```java
package com.llmocr.mcp.invoice.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;

public class JwtTestUtil {
    public static void main(String[] args) {
        String secret = "your-secret-key"; // Use same key as application
        
        String token = Jwts.builder()
            .setSubject("test-user")
            .claim("tenant_id", "acme-energy")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
            
        System.out.println("Test JWT Token:");
        System.out.println(token);
    }
}
```

Run it to generate a test token, then paste into Settings page.

---

## 🧪 Testing Without Backend

If the backend isn't running, you can still test the UI:

1. **Mock data is included** - Transaction Viewer has sample data
2. **Category data** - Will show error, but UI is functional
3. **Use for UI development** - Test layouts, responsiveness, branding

---

## 📋 Troubleshooting

### Problem: Still getting 401s after setting token

**Check:**
1. Token is saved in Settings page (see "Active" chip)
2. Backend is running: `http://localhost:8081/mcp-invoice/actuator/health`
3. Token hasn't expired
4. Token is for correct tenant (acme-energy)

**Solution:**
```bash
# Check browser console
Open DevTools → Console
Look for: "Authentication required. Please set an auth token."

# Check network tab
Network → Look at failed requests
Headers → Authorization: Bearer should be present
```

### Problem: Backend not accepting token

**Check backend logs:**
```bash
cd /path/to/mcp-invoice-server
./mvnw spring-boot:run

# Look for JWT validation errors in logs
```

**Common issues:**
- Wrong secret key
- Token expired
- Invalid tenant_id claim
- Token format incorrect

### Problem: Can't access Settings page

**Solution:**
```bash
# Directly set token via browser console
localStorage.setItem('authToken', 'YOUR_TOKEN_HERE');

# Refresh page
location.reload();
```

---

## 🔄 Current Request Flow

```
Admin UI → API Call → Backend MCP Server
          ↓
    [Check localStorage for 'authToken']
          ↓
    [Add "Authorization: Bearer {token}" header]
          ↓
    [Backend validates JWT]
          ↓
    Success (200) or Auth Error (401)
```

---

## 📝 Environment Variables

Create `.env` file in `admin-ui/` directory:

```bash
# Backend API URL
REACT_APP_API_URL=http://localhost:8081/mcp-invoice

# Optional: Default token (for development)
# REACT_APP_DEFAULT_TOKEN=your-token-here
```

---

## ✅ Verification Steps

After setting up authentication:

1. **Open Settings page** - Should show "Token is currently configured" with green "Active" chip
2. **Go to Category Management** - Should load categories without 401 errors
3. **Check browser console** - No red errors, warnings OK
4. **Network tab** - API calls return 200, not 401

---

## 🎯 Quick Commands

```bash
# Start backend
cd /path/to/mcp-invoice-server
./mvnw spring-boot:run

# Start admin UI
cd /path/to/mcp-invoice-server/admin-ui
./start.sh

# Open admin UI
open http://localhost:3000

# Navigate to Settings
# Click ⚙️ icon in sidebar

# Save your JWT token
# Paste token → Click "Save Token"

# Test API calls
# Go to Category Management
# Should load without 401 errors
```

---

## 📞 Need Help?

1. Check browser console for detailed errors
2. Check backend logs for authentication issues
3. Verify token in Settings page (Active chip should show)
4. Test backend directly: `curl http://localhost:8081/mcp-invoice/actuator/health`

---

*Authentication Guide - October 15, 2025*  
*Issue: 401 Unauthorized - RESOLVED ✅*

