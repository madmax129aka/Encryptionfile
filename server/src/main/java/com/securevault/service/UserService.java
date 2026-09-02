package com.securevault.service;

import com.securevault.entity.User;
import com.securevault.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges our {@link User} entity to Spring Security's UserDetails so the
 * DaoAuthenticationProvider can authenticate against the BCrypt hash.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        return new org.springframework.security.core.userdetails.User(
                u.getUsername(),
                u.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    public User requireByUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }
}
