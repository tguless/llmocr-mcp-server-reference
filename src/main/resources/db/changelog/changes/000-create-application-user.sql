-- liquibase formatted sql
-- changeset system:create-mcp-invoice-app-user
-- Create the MCP Invoice application runtime user (if it doesn't exist)

-- Check if user exists and create if not
-- Using EXECUTE format for safe identifier and literal handling
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${mcp.app.db.username}') THEN
        EXECUTE format('CREATE USER %I WITH PASSWORD %L', '${mcp.app.db.username}', '${mcp.app.db.password}');
    END IF;
END
$$;

-- Grant necessary permissions to the application user
GRANT CONNECT ON DATABASE llm_ocr_db TO ${mcp.app.db.username};

-- Grant usage on both schemas
GRANT USAGE ON SCHEMA public TO ${mcp.app.db.username};
GRANT USAGE ON SCHEMA mcp_invoice TO ${mcp.app.db.username};

-- Grant read-only on public schema (for Liquibase tracking tables)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${mcp.app.db.username};

-- Grant full permissions on mcp_invoice schema (application tables)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA mcp_invoice TO ${mcp.app.db.username};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA mcp_invoice TO ${mcp.app.db.username};

-- Grant permissions for future tables in mcp_invoice schema
ALTER DEFAULT PRIVILEGES IN SCHEMA mcp_invoice GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${mcp.app.db.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA mcp_invoice GRANT USAGE, SELECT ON SEQUENCES TO ${mcp.app.db.username};

-- rollback DROP USER IF EXISTS ${mcp.app.db.username};

