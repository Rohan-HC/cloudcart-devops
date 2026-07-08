package com.cloudcart.userservice.service;

import com.cloudcart.userservice.entity.UserAccount;
import com.cloudcart.userservice.repository.UserAccountRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserPrincipalService implements UserDetailsService {

    private final UserAccountRepository repository;

    public UserPrincipalService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = repository.findByEmail(username.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return toPrincipal(account);
    }

    public UserDetails toPrincipal(UserAccount account) {
        return new User(
                account.getEmail(),
                account.getPasswordHash(),
                authorities(account.getRole().name()));
    }

    private Collection<? extends GrantedAuthority> authorities(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
