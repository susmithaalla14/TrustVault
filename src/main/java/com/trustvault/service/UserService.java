package com.trustvault.service;

import com.trustvault.model.Role;
import com.trustvault.model.User;
import com.trustvault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /**
     * Registers a new user.
     * Prevents self-assigned administrator privileges: any registration is strictly
     * assigned OWNER role (or VIEWER), never ADMIN.
     */
    @Transactional
    public User registerUser(String username, String email, String fullName, String rawPassword, HttpServletRequest request) {
        String cleanUsername = username != null ? username.trim().toLowerCase() : "";
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";

        if (cleanUsername.length() < 3 || cleanUsername.length() > 50) {
            throw new IllegalArgumentException("Username must be between 3 and 50 characters.");
        }

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new IllegalArgumentException("Username '" + cleanUsername + "' is already registered.");
        }

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email address '" + cleanEmail + "' is already registered.");
        }

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }

        User user = User.builder()
                .username(cleanUsername)
                .email(cleanEmail)
                .fullName(fullName != null ? fullName.trim() : cleanUsername)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.OWNER) // Hardcoded to prevent privilege self-escalation to ADMIN
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        auditLogService.logUserRegistration(saved, request);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public User getRequiredUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    @Transactional
    public void updateProfile(User currentUser, String fullName, String email, HttpServletRequest request) {
        if (fullName != null && !fullName.isBlank()) {
            currentUser.setFullName(fullName.trim());
        }
        if (email != null && !email.isBlank()) {
            String cleanEmail = email.trim().toLowerCase();
            if (!cleanEmail.equalsIgnoreCase(currentUser.getEmail()) && userRepository.existsByEmail(cleanEmail)) {
                throw new IllegalArgumentException("Email is already in use by another account.");
            }
            currentUser.setEmail(cleanEmail);
        }
        userRepository.save(currentUser);
        auditLogService.logEvent(currentUser.getUsername(), currentUser.getId(), currentUser.getRole().name(),
                "PROFILE_UPDATE", "USER", currentUser.getId(), currentUser.getUsername(),
                "SUCCESS", "User updated profile information", request);
    }

    @Transactional
    public void changePassword(User currentUser, String currentPassword, String newPassword, HttpServletRequest request) {
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            auditLogService.logEvent(currentUser.getUsername(), currentUser.getId(), currentUser.getRole().name(),
                    "PASSWORD_CHANGE_FAILED", "USER", currentUser.getId(), currentUser.getUsername(),
                    "DENIED", "Current password verification failed", request);
            throw new IllegalArgumentException("Current password does not match our records.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters long.");
        }

        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);

        auditLogService.logEvent(currentUser.getUsername(), currentUser.getId(), currentUser.getRole().name(),
                "PASSWORD_CHANGE_SUCCESS", "USER", currentUser.getId(), currentUser.getUsername(),
                "SUCCESS", "User changed account password", request);
    }

    // ==========================================
    // Administrative Operations (Admin Only)
    // ==========================================

    @Transactional(readOnly = true)
    public List<User> getAllUsers(User adminUser) {
        verifyAdmin(adminUser);
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateUserRole(Long targetUserId, Role newRole, User adminUser, HttpServletRequest request) {
        verifyAdmin(adminUser);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found."));

        if (target.getId().equals(adminUser.getId()) && newRole != Role.ADMIN) {
            throw new IllegalArgumentException("Administrators cannot demote themselves.");
        }

        Role oldRole = target.getRole();
        target.setRole(newRole);
        userRepository.save(target);

        auditLogService.logEvent(adminUser.getUsername(), adminUser.getId(), adminUser.getRole().name(),
                "ADMIN_ROLE_CHANGE", "USER", target.getId(), target.getUsername(),
                "SUCCESS", String.format("Changed role of '%s' from %s to %s", target.getUsername(), oldRole, newRole), request);
    }

    @Transactional
    public void toggleUserStatus(Long targetUserId, User adminUser, HttpServletRequest request) {
        verifyAdmin(adminUser);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found."));

        if (target.getId().equals(adminUser.getId())) {
            throw new IllegalArgumentException("Administrators cannot disable their own account.");
        }

        target.setEnabled(!target.isEnabled());
        userRepository.save(target);

        auditLogService.logEvent(adminUser.getUsername(), adminUser.getId(), adminUser.getRole().name(),
                "ADMIN_STATUS_CHANGE", "USER", target.getId(), target.getUsername(),
                "SUCCESS", String.format("Account '%s' status changed to %s", target.getUsername(), target.isEnabled() ? "ENABLED" : "DISABLED"), request);
    }

    private void verifyAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrative privileges required for this operation.");
        }
    }
}
