-- ====================================================================
-- PostgreSQL Initialization Script for MCP Invoice Server
-- ====================================================================
-- This script runs when the PostgreSQL container first starts.
-- The database owner is 'liquibase_user' (POSTGRES_USER in docker-compose).
-- ====================================================================

-- Grant CREATE permission on the database to liquibase_user
GRANT CREATE ON DATABASE llm_ocr_db TO liquibase_user;

-- Grant CREATEROLE privilege to liquibase_user
-- This allows Liquibase migrations to create the application user
ALTER USER liquibase_user CREATEROLE;

-- Create the mcp_invoice schema
CREATE SCHEMA IF NOT EXISTS mcp_invoice;

-- ====================================================================
-- Create Application User for Local Development
-- ====================================================================
-- This user is normally created by Liquibase migrations, but we also
-- create it here so that Spring can connect when running from an IDE
-- (where environment variables from docker-compose aren't available).
-- ====================================================================

-- Create mcp_invoice_app (used by docker-compose)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'mcp_invoice_app') THEN
        CREATE USER mcp_invoice_app WITH PASSWORD 'mcp_invoice_app_password';
    END IF;
END
$$;

-- Create mcp_invoice_user (default in application.yml for IDE dev)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'mcp_invoice_user') THEN
        CREATE USER mcp_invoice_user WITH PASSWORD 'mcp_invoice_password';
    END IF;
END
$$;

-- Grant schema access to app users
GRANT USAGE ON SCHEMA mcp_invoice TO mcp_invoice_app, mcp_invoice_user;

-- Grant table/sequence permissions (for tables created later by Liquibase)
ALTER DEFAULT PRIVILEGES FOR USER liquibase_user IN SCHEMA mcp_invoice
    GRANT ALL ON TABLES TO mcp_invoice_app, mcp_invoice_user;
ALTER DEFAULT PRIVILEGES FOR USER liquibase_user IN SCHEMA mcp_invoice
    GRANT ALL ON SEQUENCES TO mcp_invoice_app, mcp_invoice_user;
