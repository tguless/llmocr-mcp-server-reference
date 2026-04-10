package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.domain.Tenant;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.TenantRepository;
import com.llmocr.mcp.invoice.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User Management Controller for Admin UI
 * 
 * Handles user administration operations:
 * - Global Admin can manage all users across all tenants
 * - Tenant Admin can manage users within their tenant
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserManagementController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    /**
     * Get all users (Global Admin only)
     * Or get users for specific tenant (Tenant Admin)
     */
    @GetMapping
    public ResponseEntity<?> getUsers(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        List<User> users;
        
        if (currentUser.getRole() == User.UserRole.GLOBAL_ADMIN) {
            // Global admin can see all users
            users = userRepository.findAll();
        } else if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            // Tenant admin can only see users in their tenant
            if (currentUser.getTenantId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tenant admin must be assigned to a tenant"));
            }
            users = userRepository.findByTenantId(currentUser.getTenantId());
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        // Filter by status if provided
        if (status != null) {
            try {
                User.UserStatus statusEnum = User.UserStatus.valueOf(status);
                users = users.stream()
                        .filter(u -> u.getStatus() == statusEnum)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid status: " + status));
            }
        }

        // Convert to response DTOs
        List<Map<String, Object>> response = users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get pending users awaiting tenant assignment
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingUsers(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("user");
        
        if (currentUser == null || currentUser.getRole() == User.UserRole.USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        List<User> pendingUsers = userRepository.findByStatus(User.UserStatus.PENDING);

        List<Map<String, Object>> response = pendingUsers.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Assign user to tenant and activate
     */
    @PostMapping("/{userId}/assign-tenant")
    public ResponseEntity<?> assignTenant(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        User currentUser = (User) httpRequest.getAttribute("user");
        String tenantId = request.get("tenantId");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        if (tenantId == null || tenantId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tenant ID is required"));
        }

        // Check permissions
        if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            // Tenant admin can only assign to their own tenant
            if (!tenantId.equals(currentUser.getTenantId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tenant admin can only assign users to their own tenant"));
            }
        } else if (currentUser.getRole() != User.UserRole.GLOBAL_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        // Find and update user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        String previousTenant = user.getTenantId();
        user.setTenantId(tenantId);
        user.setStatus(User.UserStatus.ACTIVE);
        user = userRepository.save(user);

        if (previousTenant == null) {
            log.info("User {} assigned to tenant {} by {}", 
                    user.getUsername(), tenantId, currentUser.getUsername());
        } else {
            log.info("User {} tenant changed from {} to {} by {}", 
                    user.getUsername(), previousTenant, tenantId, currentUser.getUsername());
        }

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Update user role
     */
    @PutMapping("/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        User currentUser = (User) httpRequest.getAttribute("user");
        String roleStr = request.get("role");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        if (roleStr == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Role is required"));
        }

        User.UserRole newRole;
        try {
            newRole = User.UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role: " + roleStr));
        }

        // Check permissions
        if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            // Tenant admin can only promote to TENANT_ADMIN within their tenant
            if (newRole == User.UserRole.GLOBAL_ADMIN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tenant admin cannot create global admins"));
            }
        } else if (currentUser.getRole() != User.UserRole.GLOBAL_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        // Find and update user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        
        // Tenant admin can only modify users in their tenant
        if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            if (!Objects.equals(user.getTenantId(), currentUser.getTenantId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cannot modify users from other tenants"));
            }
        }

        user.setRole(newRole);
        user = userRepository.save(user);

        log.info("User {} role updated to {} by {}", 
                user.getUsername(), newRole, currentUser.getUsername());

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Update user status (suspend, activate, etc.)
     */
    @PutMapping("/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        User currentUser = (User) httpRequest.getAttribute("user");
        String statusStr = request.get("status");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        if (statusStr == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Status is required"));
        }

        User.UserStatus newStatus;
        try {
            newStatus = User.UserStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + statusStr));
        }

        // Only admins can change user status
        if (currentUser.getRole() == User.UserRole.USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        // Find and update user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        
        // Tenant admin can only modify users in their tenant
        if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            if (!Objects.equals(user.getTenantId(), currentUser.getTenantId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cannot modify users from other tenants"));
            }
        }

        user.setStatus(newStatus);
        user = userRepository.save(user);

        log.info("User {} status updated to {} by {}", 
                user.getUsername(), newStatus, currentUser.getUsername());

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Delete/deactivate user
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {
        
        User currentUser = (User) httpRequest.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        // Only admins can delete users
        if (currentUser.getRole() == User.UserRole.USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        // Find user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        
        // Tenant admin can only delete users in their tenant
        if (currentUser.getRole() == User.UserRole.TENANT_ADMIN) {
            if (!Objects.equals(user.getTenantId(), currentUser.getTenantId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cannot delete users from other tenants"));
            }
        }

        // Prevent self-deletion
        if (user.getId().equals(currentUser.getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete your own account"));
        }

        // Soft delete - set status to DEACTIVATED
        user.setStatus(User.UserStatus.DEACTIVATED);
        user.setIsActive(false);
        userRepository.save(user);

        log.info("User {} deactivated by {}", user.getUsername(), currentUser.getUsername());

        return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
    }

    /**
     * Get all active tenants for dropdown selection
     */
    @GetMapping("/tenants")
    public ResponseEntity<?> getTenants(HttpServletRequest httpRequest) {
        User currentUser = (User) httpRequest.getAttribute("user");
        
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        // Only admins can view tenants list
        if (currentUser.getRole() == User.UserRole.USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        List<Tenant> tenants;
        
        if (currentUser.getRole() == User.UserRole.GLOBAL_ADMIN) {
            // Global admin can see all active tenants
            tenants = tenantRepository.findByActiveTrue();
        } else {
            // Tenant admin can only see their own tenant
            if (currentUser.getTenantId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tenant admin must be assigned to a tenant"));
            }
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantIdAndActiveTrue(currentUser.getTenantId());
            tenants = tenantOpt.map(List::of).orElse(List.of());
        }

        // Convert to response DTOs
        List<Map<String, Object>> response = tenants.stream()
                .map(this::toTenantResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Convert User entity to response DTO
     */
    private Map<String, Object> toUserResponse(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("fullName", user.getFullName());
        response.put("tenantId", user.getTenantId());
        response.put("role", user.getRole().name());
        response.put("status", user.getStatus().name());
        response.put("isActive", user.getIsActive());
        response.put("isEmailVerified", user.getIsEmailVerified());
        response.put("lastLoginAt", user.getLastLoginAt());
        response.put("createdAt", user.getCreatedAt());
        response.put("updatedAt", user.getUpdatedAt());
        response.put("createdBy", user.getCreatedBy());
        return response;
    }

    /**
     * Convert Tenant entity to response DTO
     */
    private Map<String, Object> toTenantResponse(Tenant tenant) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", tenant.getId());
        response.put("tenantId", tenant.getTenantId());
        response.put("tenantName", tenant.getTenantName());
        response.put("description", tenant.getDescription());
        response.put("active", tenant.getActive());
        return response;
    }
}

