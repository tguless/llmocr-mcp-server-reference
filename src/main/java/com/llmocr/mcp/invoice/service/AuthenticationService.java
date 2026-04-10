package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.RefreshToken;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.RefreshTokenRepository;
import com.llmocr.mcp.invoice.repository.UserRepository;
import com.llmocr.mcp.invoice.security.JwtIntrospectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtIntrospectionService jwtIntrospectionService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final com.llmocr.mcp.invoice.repository.TenantRepository tenantRepository;
    private final com.llmocr.mcp.invoice.repository.CategoryRepository categoryRepository;

    /**
     * Register a new user
     * The first user in the system automatically becomes a GLOBAL_ADMIN.
     * Subsequent users are created in PENDING status without a tenant.
     * A Global Admin or Tenant Admin must assign them to a tenant.
     */
    @Transactional
    public Map<String, Object> register(String username, String email, String password, 
                                       String firstName, String lastName) {
        
        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        // Check if this is the first user (bootstrap scenario)
        long userCount = userRepository.count();
        boolean isFirstUser = (userCount == 0);

        User user;
        if (isFirstUser) {
            // First user becomes GLOBAL_ADMIN with ACTIVE status
            user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(firstName)
                    .lastName(lastName)
                    .tenantId("default")  // Assign to default tenant
                    .role(User.UserRole.GLOBAL_ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .isActive(true)
                    .isEmailVerified(false)
                    .createdBy("system")
                    .build();

            user = userRepository.save(user);
            log.info("🚀 BOOTSTRAP: First user registered as GLOBAL_ADMIN: username={}, email={}, tenantId=default", 
                    username, email);
        } else {
            // Check if there's an unclaimed tenant with the same key as the username
            String potentialTenantId = username.toLowerCase();
            Optional<com.llmocr.mcp.invoice.domain.Tenant> unclaimedTenant = findUnclaimedTenant(potentialTenantId);
            
            if (unclaimedTenant.isPresent()) {
                String claimedTenantId = unclaimedTenant.get().getTenantId();
                
                // Auto-link to unclaimed tenant and make user a TENANT_ADMIN
                user = User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .firstName(firstName)
                        .lastName(lastName)
                        .tenantId(claimedTenantId)
                        .role(User.UserRole.TENANT_ADMIN)
                        .status(User.UserStatus.ACTIVE)
                        .isActive(true)
                        .isEmailVerified(false)
                        .build();

                user = userRepository.save(user);
                log.info("✅ AUTO-LINKED: User '{}' linked to unclaimed tenant '{}' as TENANT_ADMIN", 
                        username, claimedTenantId);
                
                // Create default GENERIC category for the newly claimed tenant
                createDefaultCategory(claimedTenantId, username);
            } else {
                // No unclaimed tenant - create user in PENDING status (awaiting tenant assignment)
                user = User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .firstName(firstName)
                        .lastName(lastName)
                        .tenantId(null)  // No tenant assigned yet
                        .role(User.UserRole.USER)
                        .status(User.UserStatus.PENDING)
                        .isActive(true)
                        .isEmailVerified(false)
                        .build();

                user = userRepository.save(user);
                log.info("New user registered (pending tenant assignment): username={}, email={}", username, email);
            }
        }

        // Generate tokens
        String accessToken = jwtTokenService.generateToken(user);
        String refreshToken = createRefreshToken(user);

        return createAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Authenticate user and generate tokens
     */
    @Transactional
    public Map<String, Object> login(String usernameOrEmail, String password) {
        // Find user by username or email
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new RuntimeException("User account is deactivated");
        }

        // Check user status
        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new RuntimeException("User account is suspended");
        }

        if (user.getStatus() == User.UserStatus.DEACTIVATED) {
            throw new RuntimeException("User account is deactivated");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Update last login
        user.updateLastLogin();
        userRepository.save(user);

        log.info("User logged in: username={}, tenantId={}, status={}", 
                user.getUsername(), user.getTenantId(), user.getStatus());

        // Generate tokens
        String accessToken = jwtTokenService.generateToken(user);
        String refreshToken = createRefreshToken(user);

        return createAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public Map<String, Object> refreshAccessToken(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new RuntimeException("Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();

        String storedHubToken = user.getHubToken();
        if (storedHubToken != null && !storedHubToken.isBlank()) {
            JwtIntrospectionService.TokenValidationResult hubResult =
                    jwtIntrospectionService.validateToken(storedHubToken);
            if (!hubResult.isValid()) {
                refreshToken.revoke();
                refreshTokenRepository.save(refreshToken);
                user.setHubToken(null);
                userRepository.save(user);
                log.info("Refresh denied: PaperIQ hub token revoked/expired for user={}", user.getEmail());
                throw new RuntimeException("PaperIQ session ended — sign in again");
            }
        }

        String newAccessToken = jwtTokenService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 300);

        return response;
    }

    /**
     * Logout user and revoke refresh tokens
     */
    @Transactional
    public void logout(String refreshTokenString) {
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByToken(refreshTokenString);
        refreshToken.ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
            log.info("User logged out: userId={}", token.getUser().getId());
        });
    }

    /**
     * Check if a tenant exists and is unclaimed (has no users assigned to it)
     */
    private Optional<com.llmocr.mcp.invoice.domain.Tenant> findUnclaimedTenant(String tenantId) {
        Optional<com.llmocr.mcp.invoice.domain.Tenant> tenant = tenantRepository.findByTenantIdAndActiveTrue(tenantId);
        
        if (tenant.isPresent()) {
            // Check if any users are assigned to this tenant
            List<User> usersInTenant = userRepository.findByTenantId(tenantId);
            
            if (usersInTenant.isEmpty()) {
                log.debug("Found unclaimed tenant: {}", tenantId);
                return tenant;
            } else {
                log.debug("Tenant {} is already claimed ({} users)", tenantId, usersInTenant.size());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Create default GENERIC category for a newly claimed tenant
     */
    private void createDefaultCategory(String tenantId, String createdBy) {
        try {
            com.llmocr.mcp.invoice.domain.Category genericCategory = com.llmocr.mcp.invoice.domain.Category.builder()
                    .tenantId(tenantId)
                    .categoryCode("GENERIC")
                    .categoryName("Generic")
                    .description("Generic catch all category if nothing else fits")
                    .isPassThrough(false)
                    .isCredit(false)
                    .isBillable(true)
                    .keywords("other,misc,miscellaneous,general,various")
                    .examples("Miscellaneous items, General expenses")
                    .categorizationInstructions("Use this category for items that don't fit into any specific category")
                    .displayOrder(999) // Show at bottom of list
                    .isActive(true)
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
            
            categoryRepository.save(genericCategory);
            log.info("✅ Created default GENERIC category for tenant: {}", tenantId);
            
        } catch (Exception e) {
            // Don't fail registration if category creation fails
            log.error("Failed to create default GENERIC category for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    /**
     * Create refresh token for user
     */
    private String createRefreshToken(User user) {
        String tokenString = jwtTokenService.generateRefreshToken(user);
        
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenString)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenString;
    }

    /**
     * Create authentication response
     */
    private Map<String, Object> createAuthResponse(User user, String accessToken, String refreshToken) {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 300); // 5 minutes — clients must use refresh flow
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("fullName", user.getFullName());
        userInfo.put("tenantId", user.getTenantId());
        userInfo.put("role", user.getRole().name());
        userInfo.put("status", user.getStatus().name());
        
        response.put("user", userInfo);
        
        return response;
    }

    /**
     * Change password for authenticated user
     */
    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        log.info("Change password request for user: {}", user.getUsername());

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Verify new password and confirmation match
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match");
        }

        // Verify new password is different from current
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new RuntimeException("New password must be different from current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Send confirmation email
        try {
            emailService.sendPasswordChangedEmail(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.error("Failed to send password changed confirmation email", e);
            // Don't fail the operation if email fails
        }

        log.info("Password changed successfully for user: {}", user.getUsername());
    }

    /**
     * Clean up expired tokens (can be scheduled)
     */
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Expired refresh tokens cleaned up");
    }

    /**
     * Authenticate (or provision) a local admin user from trusted SSO identity claims.
     */
    @Transactional
    public Map<String, Object> loginWithSsoIdentity(String email, String tenantId, String preferredUsername, String hubToken) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("SSO identity is missing email claim");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new RuntimeException("SSO identity is missing tenant claim");
        }

        String normalizedTenantId = tenantId.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseGet(() -> provisionSsoUser(email, normalizedTenantId, preferredUsername));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("User account is deactivated");
        }
        if (user.getStatus() == User.UserStatus.SUSPENDED || user.getStatus() == User.UserStatus.DEACTIVATED) {
            throw new RuntimeException("User account is not allowed to login");
        }

        boolean updated = false;
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            user.setTenantId(normalizedTenantId);
            updated = true;
        }
        if (user.getStatus() == User.UserStatus.PENDING) {
            user.setStatus(User.UserStatus.ACTIVE);
            updated = true;
        }
        if (updated) {
            user = userRepository.save(user);
        }

        user.setHubToken(hubToken);
        user.updateLastLogin();
        userRepository.save(user);

        String accessToken = jwtTokenService.generateToken(user);
        String refreshToken = createRefreshToken(user);
        return createAuthResponse(user, accessToken, refreshToken);
    }

    private User provisionSsoUser(String email, String tenantId, String preferredUsername) {
        ensureTenantExists(tenantId);

        List<User> tenantUsers = userRepository.findByTenantId(tenantId);
        User.UserRole role = tenantUsers.isEmpty() ? User.UserRole.TENANT_ADMIN : User.UserRole.USER;
        String username = resolveUniqueUsername(preferredUsername, email);
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;

        User created = User.builder()
                .username(username)
                .email(email.trim().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(localPart)
                .lastName("")
                .tenantId(tenantId)
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .isActive(true)
                .isEmailVerified(true)
                .createdBy("sso-provision")
                .build();

        User saved = userRepository.save(created);
        log.info("Provisioned invoice SSO user '{}' for tenant '{}'", saved.getEmail(), tenantId);
        return saved;
    }

    private void ensureTenantExists(String tenantId) {
        if (tenantRepository.existsByTenantId(tenantId)) {
            return;
        }
        tenantRepository.save(com.llmocr.mcp.invoice.domain.Tenant.builder()
                .tenantId(tenantId)
                .tenantName(tenantId.toUpperCase(Locale.ROOT))
                .description("Auto-provisioned tenant from SSO login")
                .active(true)
                .createdBy("sso-provision")
                .updatedBy("sso-provision")
                .build());
    }

    private String resolveUniqueUsername(String preferredUsername, String email) {
        String base = sanitizeUsername(preferredUsername);
        if (base.isBlank()) {
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            base = sanitizeUsername(local);
        }
        if (base.isBlank()) {
            base = "sso-user";
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String sanitizeUsername(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }
}

