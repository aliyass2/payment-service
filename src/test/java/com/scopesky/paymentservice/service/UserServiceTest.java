package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.user.CreateUserRequest;
import com.scopesky.paymentservice.dto.user.UserResponse;
import com.scopesky.paymentservice.exception.InvalidOperationException;
import com.scopesky.paymentservice.exception.UserNotFoundException;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.enums.UserStatus;
import com.scopesky.paymentservice.repository.UserRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private UserService userService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .referenceId("USR-TESTREF1")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createUser_validRequest_savesUserAndDefaultWallet() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        UserResponse response = userService.createUser(
                new CreateUserRequest("Alice", "Smith", "alice@example.com", null));

        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(walletRepository).save(any());
    }

    @Test
    void createUser_duplicateEmail_throwsInvalidOperationException() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("Alice", "Smith", "alice@example.com", null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getUserById_existingId_returnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        UserResponse response = userService.getUserById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void suspendUser_activeUser_updatesStatusToSuspended() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.suspendUser(1L);

        assertThat(response.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void suspendUser_alreadySuspended_throwsInvalidOperationException() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userService.suspendUser(1L))
                .isInstanceOf(InvalidOperationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void activateUser_suspendedUser_updatesStatusToActive() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.activateUser(1L);

        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void activateUser_alreadyActive_throwsInvalidOperationException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userService.activateUser(1L))
                .isInstanceOf(InvalidOperationException.class);
    }
}
