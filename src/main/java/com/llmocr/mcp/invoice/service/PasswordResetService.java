package com.llmocr.mcp.invoice.service;

import com.llmocr.mcp.invoice.domain.PasswordResetToken;
import com.llmocr.mcp.invoice.domain.User;
import com.llmocr.mcp.invoice.repository.PasswordResetTokenRepository;
import com.llmocr.mcp.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for handling password reset functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int TOKEN_EXPIRATION_HOURS = 1;

    /**
     * Initiate password reset - send reset email
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        log.info("Password reset initiated for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Delete any existing tokens for this user
        tokenRepository.deleteByUser(user);

        // Generate new token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        // Send email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            log.info("Password reset email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email. Please try again later.");
        }
    }

    /**
     * Validate reset token
     */
    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        return tokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    /**
     * Reset password using token
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("Attempting to reset password with token");

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid password reset token"));

        if (!resetToken.isValid()) {
            throw new RuntimeException("Password reset token has expired or been used");
        }

        User user = resetToken.getUser();

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Send confirmation email
        try {
            emailService.sendPasswordChangedEmail(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.error("Failed to send password changed confirmation email", e);
            // Don't fail the operation if email fails
        }

        log.info("Password reset successful for user: {}", user.getUsername());
    }

    /**
     * Scheduled job to clean up expired tokens (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Running scheduled cleanup of expired password reset tokens");
        tokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Expired password reset tokens cleanup completed");
    }
}

