package com.college.findit.controller;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import com.college.findit.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AuthController(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/change-password")
    public String changePasswordForm(Authentication auth, Model model) {
        model.addAttribute("askCurrent", !users.findByRollNo(auth.getName()).orElseThrow().isMustChangePassword());
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String newPassword, @RequestParam String confirm,
                                 @RequestParam(required = false) String currentPassword,
                                 Authentication auth, Model model, RedirectAttributes ra) {
        AppUser u = users.findByRollNo(auth.getName()).orElseThrow();
        model.addAttribute("askCurrent", !u.isMustChangePassword());
        if (!u.isMustChangePassword()
                && (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash()))) {
            model.addAttribute("error", "Your current password is wrong.");
            return "change-password";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "change-password";
        }
        if (!newPassword.equals(confirm)) {
            model.addAttribute("error", "The two passwords do not match.");
            return "change-password";
        }
        if (newPassword.equalsIgnoreCase(u.getRollNo())) {
            model.addAttribute("error", "Choose a password different from your register number.");
            return "change-password";
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        audit.log(u.getRollNo(), "PASSWORD_CHANGED", "User changed their password");
        ra.addFlashAttribute("msg", "Password updated.");
        return "redirect:/dashboard";
    }
}
