package com.llmocr.mcp.invoice.security;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local tenant context for multi-tenancy support
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_ID = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(String userId) {
        log.debug("Setting user context: {}", userId);
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void setClientId(String clientId) {
        log.debug("Setting client context: {}", clientId);
        CLIENT_ID.set(clientId);
    }

    public static String getClientId() {
        return CLIENT_ID.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        TENANT_ID.remove();
        USER_ID.remove();
        CLIENT_ID.remove();
    }

    public static boolean hasTenantId() {
        return TENANT_ID.get() != null;
    }

    public static String getCurrentTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null) {
            log.warn("No tenant ID found in context, using default");
            return "default";
        }
        return tenantId;
    }
}
