package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.security.JwtIntrospectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSsoExchangeService {

    private final RestTemplate restTemplate;
    private final JwtIntrospectionService jwtIntrospectionService;
    private final AuthenticationService authenticationService;

    @Value("${security.admin-sso.token-endpoint:http://localhost:8080/api/mcp/admin-sso/token}")
    private String tokenEndpoint;

    @Value("${security.admin-sso.client-id}")
    private String clientId;

    @Value("${security.admin-sso.client-secret}")
    private String clientSecret;

    public Map<String, Object> exchangeCodeForLocalSession(String code, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("Missing SSO authorization code");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new RuntimeException("Missing SSO redirect URI");
        }

        String hubToken = exchangeCodeForHubToken(code.trim(), redirectUri.trim());
        JwtIntrospectionService.TokenValidationResult claims = jwtIntrospectionService.validateToken(hubToken);
        if (!claims.isValid()) {
            throw new RuntimeException("SSO token introspection failed: " + claims.getError());
        }

        return authenticationService.loginWithSsoIdentity(
                claims.getEmail(),
                claims.getTenantId(),
                claims.getEmail(),
                hubToken
        );
    }

    private String exchangeCodeForHubToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "code", code,
                "clientId", clientId,
                "clientSecret", clientSecret,
                "redirectUri", redirectUri
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to exchange SSO code with llm-ocr");
        }

        Map<String, Object> body = response.getBody();
        Object success = body.get("success");
        if (success instanceof Boolean ok && !ok) {
            throw new RuntimeException("llm-ocr rejected SSO code exchange");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = body.get("data") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : body;
        Object tokenValue = data.get("accessToken");
        if (tokenValue == null) {
            tokenValue = data.get("access_token");
        }
        if (tokenValue == null || String.valueOf(tokenValue).isBlank()) {
            throw new RuntimeException("llm-ocr did not return access token");
        }

        return String.valueOf(tokenValue);
    }
}

