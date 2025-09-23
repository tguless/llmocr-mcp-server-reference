package com.llmocr.mcp.invoice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT Authentication Filter for MCP Server using Token Introspection
 * 
 * This filter validates JWT tokens by calling the main application's introspection
 * endpoint, avoiding the need to share JWT signing secrets.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtIntrospectionService jwtIntrospectionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        
        // Only apply to MCP endpoints
        if (!requestUri.startsWith("/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract Bearer token from Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("MCP request missing Bearer token: {}", requestUri);
                respondWithUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix
            
            // Validate JWT token using introspection
            JwtIntrospectionService.TokenValidationResult validation = 
                    jwtIntrospectionService.validateToken(token);
            
            log.debug("Token validation result: valid={}, userId={}, tenantId={}, email={}, clientId={}", 
                     validation.isValid(), validation.getUserId(), validation.getTenantId(), validation.getEmail(), validation.getClientId());
            
            if (!validation.isValid()) {
                log.debug("Invalid JWT token for MCP request: {} - {}", requestUri, validation.getError());
                respondWithUnauthorized(response, "Invalid or expired token");
                return;
            }

            // Set up security context
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                            validation.getUserId(), 
                            null, 
                            new ArrayList<>()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Set up MCP security context via request attributes (the context reads from these)
            // The McpSecurityContext class reads from request attributes, not direct setters

            // Add user context to request attributes
            request.setAttribute("mcpTenantId", validation.getTenantId());
            request.setAttribute("mcpUserId", validation.getUserId());
            request.setAttribute("mcpUserEmail", validation.getEmail());
            request.setAttribute("mcpUserToken", token);
            request.setAttribute("mcpClientId", validation.getClientId());

            log.debug("MCP request authenticated for tenant: {}, user: {}", 
                    validation.getTenantId(), validation.getUserId());

            // Continue with the request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("MCP authentication filter error: {}", e.getMessage(), e);
            respondWithUnauthorized(response, "Authentication error");
        } finally {
            // Always clear security context (stateless principle)
            // Request attributes are automatically cleared after the request
            SecurityContextHolder.clearContext();
        }
    }

    private void respondWithUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\": \"unauthorized\", \"message\": \"%s\"}", message));
    }
}
