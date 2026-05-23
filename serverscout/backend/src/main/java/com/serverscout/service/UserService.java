package com.serverscout.service;

import com.serverscout.entity.User;
import com.serverscout.repository.UserRepository;
import com.serverscout.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    @Transactional
    public User createUser(String username, String password, String role, String name, String gender, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (email == null || email.isBlank() || !email.matches("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .name(name)
                .gender(gender)
                .role(role != null ? role : "USER")
                .email(email)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, String role, String name, String gender, String email, Boolean enabled) {
        User user = getUserById(id);
        if (role != null) user.setRole(role);
        if (name != null) user.setName(name);
        if (gender != null) user.setGender(gender);
        if (email != null) user.setEmail(email);
        if (enabled != null) user.setEnabled(enabled);
        return userRepository.save(user);
    }

    @Transactional
    public User updateCurrentUser(String username, String name, String gender, String email) {
        User user = getUserByUsername(username);
        if (name != null) user.setName(name);
        if (gender != null) user.setGender(gender);
        if (email != null) user.setEmail(email);
        return userRepository.save(user);
    }

    @Transactional
    public void changeCurrentUserPassword(String username, String oldPassword, String newPassword) {
        User user = getUserByUsername(username);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码至少需要6个字符");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = getUserById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = getUserById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        if ("ADMIN".equals(user.getRole())) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> "ADMIN".equals(u.getRole())).count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last admin user");
            }
        }
        userRepository.delete(user);
    }

    public boolean authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> u.getEnabled())
                .map(u -> passwordEncoder.matches(password, u.getPassword()))
                .orElse(false);
    }
}
