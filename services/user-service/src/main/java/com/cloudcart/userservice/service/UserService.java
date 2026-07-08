package com.cloudcart.userservice.service;

import com.cloudcart.userservice.dto.UserResponse;
import com.cloudcart.userservice.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserAccountRepository repository;

    public UserService(UserAccountRepository repository) {
        this.repository = repository;
    }

    public UserResponse getCurrentUser(String email) {
        var account = repository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        return new UserResponse(account.getId(), account.getName(), account.getEmail(), account.getCreatedAt());
    }
}
