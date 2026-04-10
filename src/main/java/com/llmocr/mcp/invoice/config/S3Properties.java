package com.llmocr.mcp.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3/MinIO configuration properties
 */
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Properties {
    
    /**
     * Public endpoint for browser access to presigned URLs
     */
    private String publicEndpoint;
    
    /**
     * Default server endpoint for S3/MinIO
     * Used when creating new bucket configurations
     */
    private String defaultEndpoint;
    
    /**
     * Default access key for MinIO
     * Used when creating new bucket configurations
     */
    private String defaultAccessKey;
    
    /**
     * Default secret key for MinIO
     * Used when creating new bucket configurations
     */
    private String defaultSecretKey;
    
    public String getPublicEndpoint() {
        return publicEndpoint;
    }
    
    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }
    
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    public void setDefaultEndpoint(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }
    
    public String getDefaultAccessKey() {
        return defaultAccessKey;
    }
    
    public void setDefaultAccessKey(String defaultAccessKey) {
        this.defaultAccessKey = defaultAccessKey;
    }
    
    public String getDefaultSecretKey() {
        return defaultSecretKey;
    }
    
    public void setDefaultSecretKey(String defaultSecretKey) {
        this.defaultSecretKey = defaultSecretKey;
    }
}

