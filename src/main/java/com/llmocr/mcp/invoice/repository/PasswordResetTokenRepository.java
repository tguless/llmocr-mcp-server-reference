package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.PasswordResetToken;
import com.llmocr.mcp.invoice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Find a password reset token by token string
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Find all tokens for a specific user
     */
    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.user = :user")
    Optional<PasswordResetToken> findByUser(@Param("user") User user);

    /**
     * Delete all expired tokens
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiryDate < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete all tokens for a specific user
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.user = :user")
    void deleteByUser(@Param("user") User user);
}

