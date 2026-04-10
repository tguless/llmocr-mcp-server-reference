FROM ubuntu:24.04

# Install Java runtime and dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jre \
    fontconfig \
    libfreetype6 \
    curl \
    ca-certificates \
    bash \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the pre-built JAR file (built by prepare.sh or manually)
COPY target/*.jar app.jar

# Create non-root user
RUN groupadd -r mcpinvoice && useradd -r -g mcpinvoice mcpinvoice && \
    chown mcpinvoice:mcpinvoice app.jar

# Switch to non-root user
USER mcpinvoice

ENV APP_ENVIRONMENT=docker

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/mcp-invoice/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]


