package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AppUserDetails(UUID id, String email, String passwordHash, boolean enabled) implements UserDetails {

    public static AppUserDetails from(User user) {
        return new AppUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE
        );
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() { // this is important because we use email as unique identifier
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // 6. mesajdaki toString tuzağı: iz log'a düşmesin
    @Override
    public String toString() {
        return "AppUserDetails[id=" + id + ", email=" + email + "]";
    }
}
