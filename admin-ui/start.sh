#!/bin/bash

################################################################################
# Admin UI - Start Script
# Starts the React development server with proper node environment
################################################################################

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=========================================="
echo "  MCP Invoice Server - Admin UI"
echo "=========================================="
echo ""

# Setup node environment
export PATH="$HOME/.nvm/versions/node/v20.19.0/bin:$PATH"

# Check if node is available
if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found. Please install Node.js 18+ or configure nvm."
    exit 1
fi

NODE_VERSION=$(node -v)
echo -e "${GREEN}✓${NC} Node.js: $NODE_VERSION"

# Check if dependencies are installed
if [ ! -d "node_modules" ]; then
    echo ""
    echo "📦 Installing dependencies..."
    npm install
fi

echo ""
echo -e "${BLUE}Starting development server...${NC}"
echo ""
echo "Admin UI will be available at:"
echo "  → http://localhost:3000"
echo ""
echo "Backend API (must be running):"
echo "  → http://localhost:8081/mcp-invoice"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start development server
npm start

