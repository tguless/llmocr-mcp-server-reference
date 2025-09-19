FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Create non-root user
RUN groupadd -r mcpinvoice && useradd -r -g mcpinvoice mcpinvoice

# Copy the built jar
COPY --chown=mcpinvoice:mcpinvoice target/*.jar app.jar

# Switch to non-root user
USER mcpinvoice

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/mcp-invoice/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
