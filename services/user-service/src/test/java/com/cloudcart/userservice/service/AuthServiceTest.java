package com.cloudcart.userservice.service;

import com.cloudcart.userservice.config.UserServiceProperties;
import com.cloudcart.userservice.dto.AuthRequest;
import com.cloudcart.userservice.dto.RegisterRequest;
import com.cloudcart.userservice.entity.UserAccount;
import com.cloudcart.userservice.entity.UserRole;
import com.cloudcart.userservice.repository.UserAccountRepository;
import com.cloudcart.userservice.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UserPrincipalService userPrincipalService = new UserPrincipalService(repository);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(new UserServiceProperties("cloudcart-cloudcart-cloudcart-cloudcart-cloudcart-123456", 60));
    private final AuthService service = new AuthService(repository, passwordEncoder, jwtService, authenticationManager, userPrincipalService);

    @Test
    void registerCreatesToken() {
        when(repository.findByEmail("ava@example.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(new RegisterRequest("Ava", "ava@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginReturnsToken() {
        UserAccount account = new UserAccount("Ava", "ava@example.com", passwordEncoder.encode("password123"), UserRole.USER);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("ava@example.com", "password123"));
        when(repository.findByEmail("ava@example.com")).thenReturn(Optional.of(account));

        var response = service.login(new AuthRequest("ava@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
    }
}
