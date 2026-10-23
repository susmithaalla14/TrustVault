package com.trustvault.service;

import com.trustvault.model.Role;
import com.trustvault.model.User;
import com.trustvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Override
    public void run(String... args) {
        log.info("Initializing TrustVault core security and storage checks...");

        // Ensure the private storage bucket exists
        try {
            storageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Storage bucket initialization warning: {}", e.getMessage());
        }

        // Provision initial Administrator if none exists
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            log.info("Provisioning initial TrustVault Administrator account...");
            User admin = User.builder()
                    .username("admin")
                    .email("admin@trustvault.local")
                    .fullName("TrustVault Administrator")
                    .password(passwordEncoder.encode("Admin@TrustVault2026!"))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            auditLogService.logEvent("SYSTEM", null, "SYSTEM",
                    "ADMIN_PROVISIONED", "USER", admin.getId(), admin.getUsername(),
                    "SUCCESS", "Initial administrator account safely provisioned", null);
            log.info("Administrator provisioned: username='admin'");
        }

        // Provision a default demo document owner for immediate evaluation
        if (!userRepository.existsByUsername("susu_owner")) {
            User owner = User.builder()
                    .username("susu_owner")
                    .email("susu@trustvault.local")
                    .fullName("Susmitha (Document Owner)")
                    .password(passwordEncoder.encode("Owner@TrustVault2026!"))
                    .role(Role.OWNER)
                    .enabled(true)
                    .build();
            userRepository.save(owner);
            log.info("Demo document owner provisioned: username='susu_owner'");
        }

        // Provision a default demo viewer
        if (!userRepository.existsByUsername("susu_viewer")) {
            User viewer = User.builder()
                    .username("susu_viewer")
                    .email("viewer@trustvault.local")
                    .fullName("Evaluation Viewer")
                    .password(passwordEncoder.encode("Viewer@TrustVault2026!"))
                    .role(Role.VIEWER)
                    .enabled(true)
                    .build();
            userRepository.save(viewer);
            log.info("Demo viewer provisioned: username='susu_viewer'");
        }
    }
}
