package com.llmocr.mcp.invoice.config;

import com.llmocr.mcp.invoice.security.McpJwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
 * Each MCP server validates tokens by calling back to the main application.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class McpSecurityConfiguration {

    private final McpJwtAuthenticationFilter mcpJwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for API endpoints (stateless)
                .csrf(AbstractHttpConfigurer::disable)
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Stateless session management (cloud-native requirement)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Add JWT introspection filter for MCP endpoints
                .addFilterBefore(mcpJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                
                // Configure authorization rules for stateless MCP server
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        
                        // MCP endpoints - authenticated by introspection filter
                        .requestMatchers("/mcp/**").authenticated()
                        
                        // All other requests require authentication  
                        .anyRequest().authenticated()
                )
                
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins (fix CORS credentials issue)
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://*.yourdomain.com"
        ));
        
        // Allow standard HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));
        
        // Allow standard headers plus Authorization (required for Bearer tokens)
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        
        // Allow credentials for Bearer token authentication
        configuration.setAllowCredentials(true);
        
        // Cache preflight requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
