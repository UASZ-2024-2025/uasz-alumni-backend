package com.uasz.alumni.user.model;

import com.uasz.alumni.user.spi.model.RoleName;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 50, nullable = false)
    private RoleName name;

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_name"))
    @Column(name = "permission")
    private Set<String> permissions = new HashSet<>();

    public RoleEntity() {}

    public RoleEntity(RoleName name, String description, Set<String> permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions != null ? permissions : new HashSet<>();
    }

    public RoleName getName() {
        return name;
    }

    public void setName(RoleName name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}

