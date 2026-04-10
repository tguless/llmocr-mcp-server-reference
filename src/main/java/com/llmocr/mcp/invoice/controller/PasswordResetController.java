package com.llmocr.mcp.invoice.controller;

import com.llmocr.mcp.invoice.dto.PasswordResetConfirmDto;
import com.llmocr.mcp.invoice.dto.PasswordResetRequestDto;
import com.llmocr.mcp.invoice.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for password reset operations
 */
@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Initiate password reset - send reset email
     * POST /api/password-reset/request
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestDto request) {
        
        log.info("Password reset requested for email: {}", request.getEmail());

        try {
            passwordResetService.initiatePasswordReset(request.getEmail());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "If an account exists with that email, a password reset link has been sent.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Password reset request failed for email: {}", request.getEmail(), e);
            
            // Return generic message to avoid email enumeration
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "If an account exists with that email, a password reset link has been sent.");
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Validate password reset token
     * GET /api/password-reset/validate?token={token}
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        log.info("Validating password reset token");

        boolean isValid = passwordResetService.validateResetToken(token);
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        
        if (!isValid) {
            response.put("message", "Invalid or expired token");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password with token
     * POST /api/password-reset/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmDto request) {
        
        log.info("Password reset confirmation received");

        try {
            passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Password has been reset successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Password reset confirmation failed", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
}

