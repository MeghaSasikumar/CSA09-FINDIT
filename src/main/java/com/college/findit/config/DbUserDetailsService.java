package com.college.findit.config;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import com.college.findit.service.LoginAttemptService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Tells Spring Security how to load a user (by roll number) from the database. */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final LoginAttemptService attempts;

    public DbUserDetailsService(UserRepository users, LoginAttemptService attempts) {
        this.users = users;
        this.attempts = attempts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String input = username.trim();
        AppUser u = (input.contains("@")
                ? users.findByEmailIgnoreCase(input)
                : users.findByRollNo(input.toUpperCase()))
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return User.withUsername(u.getRollNo())
                .password(u.getPasswordHash())
                .roles(u.getRole().name())
                .disabled(!u.isActive())
                .accountLocked(attempts.isLocked(u.getRollNo()))
                .build();
    }
}
