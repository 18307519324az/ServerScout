package com.serverscout.config;

import com.serverscout.entity.User;
import com.serverscout.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.scan.demo-mode:false}")
    private boolean demoMode;

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String defaultPassword = "Admin@123456";
        String encodedPassword = passwordEncoder.encode(defaultPassword);

        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .password(encodedPassword)
                    .name("系统管理员")
                    .gender("MALE")
                    .role("ADMIN")
                    .email("admin@serverscout.local")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Default admin user created: admin / {}", defaultPassword);
        } else {
            // Admin already exists — reset password in demo mode
            User admin = userRepository.findByUsername("admin").orElse(null);
            if (admin != null && demoMode) {
                admin.setPassword(encodedPassword);
                userRepository.save(admin);
                log.info("Demo admin user is ready: admin / {}", defaultPassword);
            } else if (admin == null) {
                log.info("No admin user found (other users exist), skipping admin creation");
            }
        }
    }
}
