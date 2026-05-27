package com.equitycommittee.voting.security;

import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** Load by UUID string (used by JWT filter) or by email (used for login). */
    @Override
    public UserDetails loadUserByUsername(String idOrEmail) throws UsernameNotFoundException {
        User user;
        try {
            UUID id = UUID.fromString(idOrEmail);
            user = userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + idOrEmail));
        } catch (IllegalArgumentException e) {
            user = userRepository.findByEmail(idOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + idOrEmail));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPassword(),
                user.isActive(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
