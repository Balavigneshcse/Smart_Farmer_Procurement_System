package com.cropcoders.procurement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;
import com.cropcoders.procurement.repository.UserRepository;

/**
 * Creates the super admin when the app starts, from SUPERADMIN_USERNAME and SUPERADMIN_PASSWORD
 * (the .env file on the PC, environment variables on Render). The password is a secret, so it is
 * not in a migration or anywhere else in the code. An existing super admin is left as it is.
 */
@Component
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public SuperAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${SUPERADMIN_USERNAME:}") String username,
                            @Value("${SUPERADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("No super admin yet. Set SUPERADMIN_USERNAME and SUPERADMIN_PASSWORD in .env and restart.");
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.warn("Super admin not created: the username {} is already used by another account.", username);
            return;
        }
        userRepository.save(User.newStaff(username, Role.SUPER_ADMIN, username, passwordEncoder.encode(password), null));
        log.info("Super admin account {} created.", username);
    }
}
