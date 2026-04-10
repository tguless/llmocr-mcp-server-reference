package com.llmocr.mcp.invoice.repository;

import com.llmocr.mcp.invoice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    Optional<User> findByEmailAndIsActiveTrue(String email);

    List<User> findByTenantId(String tenantId);

    List<User> findByStatus(User.UserStatus status);

    List<User> findByTenantIdAndStatus(String tenantId, User.UserStatus status);
}

