package com.college.findit.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops password guessing: after 5 wrong passwords for an account, that account is locked for 15 minutes.
 * (Important because a new student's first password is their register number.)
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private static class Attempt {
        int failures;
        LocalDateTime lockedUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void failed(String rollNo) {
        attempts.compute(rollNo, (key, existing) -> {
            Attempt a = (existing == null) ? new Attempt() : existing;
            if (a.lockedUntil != null && LocalDateTime.now().isAfter(a.lockedUntil)) {
                a.failures = 0;
                a.lockedUntil = null;
            }
            a.failures++;
            if (a.failures >= MAX_FAILURES) {
                a.lockedUntil = LocalDateTime.now().plus(LOCK_TIME);
            }
            return a;
        });
    }

    public void succeeded(String rollNo) {
        attempts.remove(rollNo);
    }

    public boolean isLocked(String rollNo) {
        Attempt a = attempts.get(rollNo);
        if (a == null || a.lockedUntil == null) return false;
        if (LocalDateTime.now().isAfter(a.lockedUntil)) {
            attempts.remove(rollNo);
            return false;
        }
        return true;
    }
}
