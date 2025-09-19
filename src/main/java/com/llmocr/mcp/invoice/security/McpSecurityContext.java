package com.llmocr.mcp.invoice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MCP Security Context Helper
 * 
 * Provides stateless access to user context from HTTP request attributes
 * following OAuth 2.1 and MCP security specifications.
 */
@Slf4j
public class McpSecurityContext {

    /**
     * Get the current tenant ID from request context
     * 
     * This is extracted from the JWT token by the authentication filter
     * and stored in request attributes for stateless access.
     */
    public static String getCurrentTenantId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String tenantId = (String) request.getAttribute("mcpTenantId");
                return tenantId != null ? tenantId : "default";
            }
        } catch (Exception e) {
            log.debug("Could not get tenant ID from request context: {}", e.getMessage());
        }
        return "default";
    }

    /**
     * Get the current user ID from request context
     */
    public static String getCurrentUserId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return (String) request.getAttribute("mcpUserId");
            }
        } catch (Exception e) {
            log.debug("Could not get user ID from request context: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get the current client ID from request context
     */
    public static String getCurrentClientId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return (String) request.getAttribute("mcpClientId");
            }
        } catch (Exception e) {
            log.debug("Could not get client ID from request context: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get the current user token from request context
     * 
     * This token can be used for downstream API calls following
     * the token delegation pattern.
     */
    public static String getCurrentUserToken() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return (String) request.getAttribute("mcpUserToken");
            }
        } catch (Exception e) {
            log.debug("Could not get user token from request context: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get the current HTTP request from Spring's RequestContextHolder
     */
    private static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.debug("Could not get HTTP request from context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if the current request is properly authenticated
     */
    public static boolean isAuthenticated() {
        return getCurrentTenantId() != null && getCurrentUserId() != null;
    }

    /**
     * Get client IP address for audit logging
     */
    public static String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP address: {}", e.getMessage());
        }
        return "unknown";
    }
}
