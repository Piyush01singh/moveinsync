package com.moveinsync.mdm.config;

import com.moveinsync.mdm.model.AdminUser;
import com.moveinsync.mdm.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserBootstrap implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final MdmSecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapUser(securityProperties.getAdminUsername(), securityProperties.getAdminPassword(), "ADMIN");
        bootstrapUser(
                securityProperties.getReleaseManagerUsername(),
                securityProperties.getReleaseManagerPassword(),
                "RELEASE_MANAGER"
        );
        bootstrapUser(
                securityProperties.getProductHeadUsername(),
                securityProperties.getProductHeadPassword(),
                "PRODUCT_HEAD"
        );
    }

    private void bootstrapUser(String username, String rawPassword, String role) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalStateException("Bootstrap credentials are missing for role " + role);
        }

        adminUserRepository.findById(username).ifPresentOrElse(existing -> {
            boolean passwordMismatch = !passwordEncoder.matches(rawPassword, existing.getPasswordHash());
            boolean roleMismatch = !role.equals(existing.getRole());
            if (passwordMismatch || roleMismatch || !existing.isEnabled()) {
                existing.setPasswordHash(passwordEncoder.encode(rawPassword));
                existing.setLastPasswordChangeAt(LocalDateTime.now());
                existing.setEnabled(true);
                existing.setRole(role);
                adminUserRepository.save(existing);
                log.info("{} credentials synchronized for {}", role, username);
            }
        }, () -> {
            AdminUser user = new AdminUser();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setEnabled(true);
            user.setLastPasswordChangeAt(LocalDateTime.now());
            adminUserRepository.save(user);
            log.info("{} user bootstrapped for {}", role, username);
        });
    }
}
