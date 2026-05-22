package com.serverscout.config;

import com.serverscout.entity.User;
import com.serverscout.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
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

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("User database already has {} users", userRepository.count());
            return;
        }
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .name("系统管理员")
                .gender("MALE")
                .role("ADMIN")
                .email("admin@serverscout.local")
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Default admin user created: admin / admin123");
    }
}
