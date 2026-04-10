#!/bin/sh
set -e

# Substitute environment variables in nginx template
echo "Processing nginx.conf.template with MCP_BACKEND_URL=${MCP_BACKEND_URL}"
envsubst '${MCP_BACKEND_URL}' < /etc/nginx/templates/nginx.conf.template > /etc/nginx/conf.d/default.conf

# Test nginx configuration
nginx -t

# Start nginx
exec nginx -g 'daemon off;'

