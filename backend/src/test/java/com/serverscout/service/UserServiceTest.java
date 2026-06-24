package com.serverscout.service;

import com.serverscout.entity.User;
import com.serverscout.repository.UserRepository;
import com.serverscout.exception.BadRequestException;
import com.serverscout.exception.ConflictException;
import com.serverscout.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L).username("testuser").password("encoded_pass")
                .name("Test User").gender("MALE").role("USER")
                .email("test@example.com").enabled(true).build();
    }

    @Test
    void shouldGetUserByUsername() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        User result = userService.getUserByUsername("testuser");
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserByUsername("nobody"));
    }

    @Test
    void shouldCreateUserSuccessfully() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        User result = userService.createUser("newuser", "password", "USER", "New", "MALE", "new@test.com");

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("hashed", result.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("exists")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> userService.createUser("exists", "pass", "USER", "X", "MALE", "x@test.com"));
    }

    @Test
    void shouldRejectInvalidEmail() {
        assertThrows(BadRequestException.class,
                () -> userService.createUser("u", "pass", "USER", "X", "MALE", "bad-email"));
    }

    @Test
    void shouldUpdateCurrentUserProfile() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateCurrentUser("testuser", "Updated Name", "FEMALE", "updated@test.com");

        assertEquals("Updated Name", result.getName());
        assertEquals("FEMALE", result.getGender());
        assertEquals("updated@test.com", result.getEmail());
    }

    @Test
    void shouldChangeCurrentUserPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", "encoded_pass")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("new_encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        assertDoesNotThrow(() ->
                userService.changeCurrentUserPassword("testuser", "oldpass", "newpass"));

        assertEquals("new_encoded", testUser.getPassword());
    }

    @Test
    void shouldRejectWrongOldPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "encoded_pass")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> userService.changeCurrentUserPassword("testuser", "wrong", "newpass"));
    }

    @Test
    void shouldListAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        List<User> users = userService.listUsers();
        assertEquals(1, users.size());
    }

    @Test
    void shouldDeleteUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository).delete(testUser);
    }

    @Test
    void shouldPreventDeletingLastAdmin() {
        User admin = User.builder().id(99L).username("admin").role("ADMIN").email("a@b.com").enabled(true).build();
        when(userRepository.findAll()).thenReturn(List.of(admin));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class,
                () -> userService.deleteUser(99L));
    }
}
