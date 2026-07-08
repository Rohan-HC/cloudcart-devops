package com.cloudcart.userservice.service;

import com.cloudcart.userservice.entity.UserAccount;
import com.cloudcart.userservice.entity.UserRole;
import com.cloudcart.userservice.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPrincipalServiceTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final UserPrincipalService service = new UserPrincipalService(repository);

    @Test
    void loadsUserByUsername() {
        when(repository.findByEmail("ava@example.com"))
                .thenReturn(Optional.of(new UserAccount("Ava", "ava@example.com", "hash", UserRole.USER)));

        var userDetails = service.loadUserByUsername("ava@example.com");

        assertThat(userDetails.getUsername()).isEqualTo("ava@example.com");
    }

    @Test
    void throwsWhenUserMissing() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
