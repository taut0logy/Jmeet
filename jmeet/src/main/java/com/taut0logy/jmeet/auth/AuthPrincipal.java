package com.taut0logy.jmeet.auth;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthPrincipal implements UserDetails {

    private final String userId;
    private final String email;
    private final String passwordHash;
    private final boolean emailVerified;

    public AuthPrincipal(String userId, String email, String passwordHash, boolean emailVerified) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
    }

    public static AuthPrincipal from(AppUser user) {
        return new AuthPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.isEmailVerified());
    }

    public String userId() {
        return userId;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}
