package com.llmocr.mcp.invoice.config;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Liquibase configuration to ensure mcp_invoice schema exists before migrations run.
 * 
 * SCHEMA STRATEGY - COMPLETE ISOLATION:
 * - Liquibase tracking tables (databasechangelog, databasechangeloglock) → mcp_invoice schema
 * - Application tables (invoices, tenants, etc.) → mcp_invoice schema
 * 
 * This provides COMPLETE separation from the main LLM-OCR backend:
 * - Main app uses 'public' schema for both tracking and application tables
 * - MCP Invoice uses 'mcp_invoice' schema for both tracking and application tables
 * - Zero risk of conflicts or table name collisions
 */
@Configuration
@EnableConfigurationProperties(LiquibaseProperties.class)
@Slf4j
public class LiquibaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /**
     * Custom SpringLiquibase bean that:
     * 1. Creates the mcp_invoice schema BEFORE Liquibase runs
     * 2. Uses LIQUIBASE MASTER CREDENTIALS (not app user) for migrations
     * 
     * CRITICAL: The app user (mcp_invoice_app) doesn't exist yet - created by migration!
     */
    @Bean
    public SpringLiquibase liquibase(LiquibaseProperties properties) {
        log.info("🔧 Configuring Liquibase for MCP Invoice Server");
        
        // Create the mcp_invoice schema using LIQUIBASE MASTER CREDENTIALS
        String liquibaseUser = properties.getUser();
        String liquibasePassword = properties.getPassword();
        
        log.info("   Creating mcp_invoice schema using master user: {}", liquibaseUser);
        log.info("   Database URL: {}", datasourceUrl);
        
        try (Connection conn = DriverManager.getConnection(
                datasourceUrl,
                liquibaseUser,
                liquibasePassword);
             Statement stmt = conn.createStatement()) {
            
            log.info("✅ Connected successfully");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS mcp_invoice");
            log.info("✅ Schema mcp_invoice created/verified");
            
        } catch (Exception e) {
            log.error("❌ Failed to create schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create mcp_invoice schema. Check master user privileges.", e);
        }
        
        // Create a SEPARATE DataSource for Liquibase using MASTER CREDENTIALS
        // This is critical because the main DataSource uses app user credentials (mcp_invoice_app)
        // which doesn't exist yet!
        log.info("   Creating Liquibase DataSource with master credentials...");
        HikariDataSource liquibaseDataSource = new HikariDataSource();
        liquibaseDataSource.setJdbcUrl(datasourceUrl);
        liquibaseDataSource.setUsername(liquibaseUser);
        liquibaseDataSource.setPassword(liquibasePassword);
        liquibaseDataSource.setMaximumPoolSize(1); // Migrations need a single connection; keeps shared RDS headroom
        log.info("✅ Liquibase DataSource created");
        
        // Configure SpringLiquibase with the master credentials DataSource
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(liquibaseDataSource); // Use LIQUIBASE DataSource, not app DataSource!
        liquibase.setChangeLog(properties.getChangeLog());
        liquibase.setContexts(properties.getContexts());
        liquibase.setDefaultSchema(properties.getDefaultSchema());
        liquibase.setLiquibaseSchema(properties.getLiquibaseSchema());
        liquibase.setDropFirst(properties.isDropFirst());
        liquibase.setShouldRun(properties.isEnabled());
        liquibase.setChangeLogParameters(properties.getParameters());
        
        // Set optional properties if they exist
        if (properties.getRollbackFile() != null) {
            liquibase.setRollbackFile(properties.getRollbackFile());
        }
        if (properties.getDatabaseChangeLogTable() != null) {
            liquibase.setDatabaseChangeLogTable(properties.getDatabaseChangeLogTable());
        }
        if (properties.getDatabaseChangeLogLockTable() != null) {
            liquibase.setDatabaseChangeLogLockTable(properties.getDatabaseChangeLogLockTable());
        }
        
        log.info("✅ Liquibase configured successfully with master credentials");
        log.info("   Liquibase tracking tables will be in '{}' schema", properties.getLiquibaseSchema());
        log.info("   Application tables will be in '{}' schema", properties.getDefaultSchema());
        log.info("   Complete isolation from main LLM-OCR backend achieved!");
        return liquibase;
    }
}

