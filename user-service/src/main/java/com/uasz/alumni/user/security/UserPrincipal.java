package com.uasz.alumni.user.security;

import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final RoleName role;
    private final UserStatus status;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, RoleName role, UserStatus status) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.status = status;
        this.authorities = role != null
                ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()))
                : Collections.emptyList();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public RoleName getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDU;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIF;
    }
}

