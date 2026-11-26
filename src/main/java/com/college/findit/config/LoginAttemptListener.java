package com.college.findit.config;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import com.college.findit.service.LoginAttemptService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Counts wrong passwords and clears the count after a successful login. */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService attempts;
    private final UserRepository users;

    public LoginAttemptListener(LoginAttemptService attempts, UserRepository users) {
        this.attempts = attempts;
        this.users = users;
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String typed = event.getAuthentication().getName();
        if (typed == null) return;
        String input = typed.trim();
        Optional<AppUser> user = input.contains("@")
                ? users.findByEmailIgnoreCase(input)
                : users.findByRollNo(input.toUpperCase());
        user.ifPresent(u -> attempts.failed(u.getRollNo()));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.succeeded(event.getAuthentication().getName());
    }
}
