package com.llmocr.mcp.invoice.config;

import com.llmocr.mcp.invoice.security.McpJwtAuthenticationFilter;
import com.llmocr.mcp.invoice.security.RestApiJwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for Stateless MCP Invoice Server
 * 
 * Uses JWT token introspection to validate tokens without sharing secrets.
 * Supports both MCP protocol endpoints and REST API endpoints for Admin UI.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class McpSecurityConfiguration {
    
    private final McpJwtAuthenticationFilter mcpJwtAuthenticationFilter;
    private final RestApiJwtAuthenticationFilter restApiJwtAuthenticationFilter;
    
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002,http://localhost:8080,http://localhost:13002,https://eyesense.ai}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for API endpoints (stateless)
                .csrf(AbstractHttpConfigurer::disable)
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Configure headers: disable X-Frame-Options for PDF streaming
                // Allow PDF streaming endpoints to be embedded in iframes from admin UI
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable())
                )
                
                // Disable form login and HTTP basic (we use JWT only)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                
                // Stateless session management (cloud-native requirement)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Add JWT introspection filters
                // Order matters: REST API filter first, then MCP filter
                .addFilterBefore(restApiJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mcpJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                
                // Configure authorization rules for stateless server
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - explicitly allow without authentication
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/password-reset/**").permitAll()
                        .requestMatchers("/api/branding/**").permitAll()  // Branding needed for login page
                        
                        // MCP endpoints - authenticated by MCP introspection filter
                        .requestMatchers("/mcp/**").authenticated()
                        
                        // REST API endpoints for Admin UI - authenticated by REST API filter
                        .requestMatchers("/api/**").authenticated()
                        
                        // All other requests require authentication  
                        .anyRequest().authenticated()
                )
                
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse allowed origins from comma-separated environment variable
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        
        // Allow standard HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));
        
        // Allow all headers (including Authorization for Bearer tokens)
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials for Bearer token authentication
        configuration.setAllowCredentials(true);
        
        // Cache preflight requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
