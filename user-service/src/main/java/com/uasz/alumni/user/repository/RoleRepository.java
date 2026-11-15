package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.RoleEntity;
import com.uasz.alumni.user.spi.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, RoleName> {
}

