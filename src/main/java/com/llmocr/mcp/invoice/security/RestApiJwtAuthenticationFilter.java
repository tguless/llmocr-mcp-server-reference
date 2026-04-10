package com.llmocr.mcp.invoice.security;

import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.UserRepository;
import com.llmocr.mcp.invoice.service.JwtTokenService;
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
import java.util.Optional;

/**
 * JWT Authentication Filter for REST API endpoints (Admin UI)
 * 
 * This filter validates JWT tokens for the Admin UI REST API endpoints under /api/**
 * It first tries local JWT validation (for our own users), then falls back to introspection if needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestApiJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtIntrospectionService jwtIntrospectionService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Use servlet path to ignore context path (/mcp-invoice)
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        
        log.debug("REST API Filter - ServletPath: {}, RequestURI: {}", servletPath, requestUri);
        
        // Only apply to REST API endpoints
        // Skip public endpoints: /api/password-reset/* and /api/auth/* (except change-password)
        if (!servletPath.startsWith("/api")) {
            log.debug("REST API Filter - Skipping filter for non-API path: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip password reset endpoints (public)
        if (servletPath.startsWith("/api/password-reset")) {
            log.debug("REST API Filter - Skipping filter for public password-reset endpoint: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip branding endpoints (public - needed for login page)
        if (servletPath.startsWith("/api/branding")) {
            log.debug("REST API Filter - Skipping filter for public branding endpoint: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip auth endpoints except change-password (which requires authentication)
        if (servletPath.startsWith("/api/auth") && !servletPath.equals("/api/auth/change-password")) {
            log.debug("REST API Filter - Skipping filter for public auth endpoint: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("REST API Filter - Applying authentication for path: {}", servletPath);

        try {
            // Extract Bearer token from Authorization header OR query parameter
            String token = null;
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7); // Remove "Bearer " prefix
            } else {
                // Check for token in query parameter (for iframe PDF viewing)
                token = request.getParameter("token");
            }
            
            if (token == null || token.isEmpty()) {
                log.debug("REST API request missing Bearer token: {}", requestUri);
                respondWithUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }
            
            // Try local JWT validation first (for Admin UI users)
            try {
                String username = jwtTokenService.extractUsername(token);
                if (jwtTokenService.validateToken(token, username)) {
                    // Local token is valid
                    Long userId = jwtTokenService.extractUserId(token);
                    String tenantId = jwtTokenService.extractTenantId(token);
                    
                    log.debug("REST API local token validation: username={}, tenantId={}", username, tenantId);
                    
                    // Load user from database
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isEmpty()) {
                        log.warn("User not found for userId: {}", userId);
                        respondWithUnauthorized(response, "User not found");
                        return;
                    }
                    
                    User user = userOpt.get();

                    // Set up security context
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Set up context with User object
                    request.setAttribute("tenantId", tenantId);
                    request.setAttribute("userId", userId.toString());
                    request.setAttribute("username", username);
                    request.setAttribute("user", user);

                    log.debug("REST API request authenticated (local): tenant={}, user={}, role={}", 
                            tenantId, username, user.getRole());
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (Exception localValidationError) {
                // Local validation failed, try introspection
                log.debug("Local token validation failed, trying introspection: {}", localValidationError.getMessage());
            }
            
            // Fall back to MCP introspection (for tokens from main app)
            JwtIntrospectionService.TokenValidationResult validation = 
                    jwtIntrospectionService.validateToken(token);
            
            if (!validation.isValid()) {
                log.debug("Invalid JWT token for REST API request: {} - {}", requestUri, validation.getError());
                respondWithUnauthorized(response, "Invalid or expired token");
                return;
            }

            // Set up security context for introspected token
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            validation.getUserId(), 
                            null, 
                            new ArrayList<>()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Set up tenant context
            request.setAttribute("tenantId", validation.getTenantId());
            request.setAttribute("userId", validation.getUserId());
            request.setAttribute("userEmail", validation.getEmail());
            request.setAttribute("userToken", token);
            request.setAttribute("clientId", validation.getClientId());

            log.debug("REST API request authenticated (introspection): tenant={}, user={}", 
                    validation.getTenantId(), validation.getUserId());

            // Continue with the request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("REST API authentication filter error: {}", e.getMessage(), e);
            respondWithUnauthorized(response, "Authentication error");
        } finally {
            // Always clear security context (stateless)
            SecurityContextHolder.clearContext();
        }
    }

    private void respondWithUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"status\":401}", message));
    }
}

