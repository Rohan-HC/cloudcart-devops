package com.cloudcart.userservice.service;

import com.cloudcart.userservice.dto.AuthRequest;
import com.cloudcart.userservice.dto.AuthResponse;
import com.cloudcart.userservice.dto.RegisterRequest;
import com.cloudcart.userservice.entity.UserAccount;
import com.cloudcart.userservice.entity.UserRole;
import com.cloudcart.userservice.repository.UserAccountRepository;
import com.cloudcart.userservice.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserPrincipalService userPrincipalService;

    public AuthService(UserAccountRepository repository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserPrincipalService userPrincipalService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userPrincipalService = userPrincipalService;
    }

    public AuthResponse register(RegisterRequest request) {
        repository.findByEmail(request.email()).ifPresent(user -> {
            throw new IllegalArgumentException("email already exists");
        });

        UserAccount saved = repository.save(new UserAccount(
                request.name(),
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                UserRole.USER
        ));

        return new AuthResponse(jwtService.generateToken(userPrincipalService.toPrincipal(saved)), "Bearer");
    }

    public AuthResponse login(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new IllegalArgumentException("invalid credentials", ex);
        }

        var user = repository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return new AuthResponse(jwtService.generateToken(userPrincipalService.toPrincipal(user)), "Bearer");
    }
}
