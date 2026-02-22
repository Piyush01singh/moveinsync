package com.moveinsync.mdm.service;

import com.moveinsync.mdm.model.AdminUser;
import com.moveinsync.mdm.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found: " + username));

        return User.withUsername(adminUser.getUsername())
                .password(adminUser.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + adminUser.getRole())))
                .disabled(!adminUser.isEnabled())
                .build();
    }
}
