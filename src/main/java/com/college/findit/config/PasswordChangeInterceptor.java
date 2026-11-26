package com.college.findit.config;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Forces every new student to set their own password on first login. */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    private final UserRepository users;

    public PasswordChangeInterceptor(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            AppUser u = users.findByRollNo(auth.getName()).orElse(null);
            if (u != null && u.isMustChangePassword()) {
                response.sendRedirect(request.getContextPath() + "/change-password");
                return false;
            }
        }
        return true;
    }
}
