package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.UserRepository;
import com.llmocr.mcp.invoice.service.AdminSsoExchangeService;
import com.llmocr.mcp.invoice.service.AuthenticationService;
import com.llmocr.mcp.invoice.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Authentication Controller for Admin UI
 * 
 * Provides login, registration, and token refresh endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AdminSsoExchangeService adminSsoExchangeService;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    @Value("${security.auth.local-registration-enabled:false}")
    private boolean localRegistrationEnabled;
    @Value("${security.auth.local-password-login-enabled:false}")
    private boolean localPasswordLoginEnabled;

    /**
     * Change password for authenticated user
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            HttpServletRequest httpRequest,
            @RequestBody Map<String, String> request) {
        try {
            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");
            String confirmPassword = request.get("confirmPassword");

            if (currentPassword == null || newPassword == null || confirmPassword == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "All fields are required"));
            }

            // Get user ID from request attributes (set by RestApiJwtAuthenticationFilter)
            String userIdStr = (String) httpRequest.getAttribute("userId");
            if (userIdStr == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            Long userId = Long.parseLong(userIdStr);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Call the authentication service to change password
            authenticationService.changePassword(user, currentPassword, newPassword, confirmPassword);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password changed successfully"
            ));

        } catch (RuntimeException e) {
            log.error("Password change failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Password change error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while changing password"));
        }
    }

    /**
     * Register a new user
     * Users are created in PENDING status and must be assigned to a tenant by an admin
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        if (!localRegistrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Local registration is disabled. Please register via PaperIQ."));
        }
        try {
            String username = request.get("username");
            String email = request.get("email");
            String password = request.get("password");
            String firstName = request.get("firstName");
            String lastName = request.get("lastName");

            // Validate required fields
            if (username == null || email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing required fields: username, email, and password are required"));
            }

            Map<String, Object> response = authenticationService.register(
                    username, email, password, firstName, lastName);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Login with username/email and password
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        if (!localPasswordLoginEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Local password login is disabled. Please sign in with PaperIQ."));
        }
        try {
            String usernameOrEmail = request.get("usernameOrEmail");
            String password = request.get("password");

            if (usernameOrEmail == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing username/email or password"));
            }

            Map<String, Object> response = authenticationService.login(usernameOrEmail, password);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Exchange llm-ocr admin SSO authorization code for local invoice admin session.
     */
    @PostMapping("/sso/exchange")
    public ResponseEntity<?> exchangeSsoCode(@RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            String redirectUri = request.get("redirectUri");
            if (code == null || redirectUri == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing required fields: code and redirectUri"));
            }
            Map<String, Object> response = adminSsoExchangeService.exchangeCodeForLocalSession(code, redirectUri);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("SSO exchange failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Refresh access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");

            if (refreshToken == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing refresh token"));
            }

            Map<String, Object> response = authenticationService.refreshAccessToken(refreshToken);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Logout and revoke refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");

            if (refreshToken != null) {
                authenticationService.logout(refreshToken);
            }

            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("message", "Logged out"));
        }
    }

    /**
     * Check if system needs bootstrap (first user)
     */
    @GetMapping("/bootstrap-status")
    public ResponseEntity<?> checkBootstrapStatus() {
        long userCount = userRepository.count();
        boolean needsBootstrap = (userCount == 0);
        
        return ResponseEntity.ok(Map.of(
            "needsBootstrap", needsBootstrap,
            "userCount", userCount,
            "message", needsBootstrap 
                ? "System is empty. First user will become Global Administrator."
                : "System has users. New registrations will be pending approval."
        ));
    }

    @GetMapping("/policy")
    public ResponseEntity<?> authPolicy() {
        return ResponseEntity.ok(Map.of(
                "localRegistrationEnabled", localRegistrationEnabled,
                "localPasswordLoginEnabled", localPasswordLoginEnabled
        ));
    }
}

