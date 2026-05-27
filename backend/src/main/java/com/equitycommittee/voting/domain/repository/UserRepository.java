package com.equitycommittee.voting.domain.repository;

import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRoleInAndActiveTrue(Collection<Role> roles);
    List<User> findByRoleInAndActiveTrue(Collection<Role> roles);
}
