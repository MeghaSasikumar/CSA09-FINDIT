package com.college.findit.controller;

import com.college.findit.repo.NotificationRepository;
import com.college.findit.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Things every page needs: the unread-notification count, and a friendly message for oversized uploads. */
@ControllerAdvice
public class GlobalAdvice {

    private final UserRepository users;
    private final NotificationRepository notifications;

    public GlobalAdvice(UserRepository users, NotificationRepository notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication auth) {
        if (auth == null) return 0;
        return users.findByRollNo(auth.getName()).map(notifications::countByUserAndSeenFalse).orElse(0L);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "That file is bigger than 2 MB. Please choose a smaller one.");
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin")) return "redirect:/admin/users";
        if (uri.startsWith("/found")) return "redirect:/found/new";
        return "redirect:/lost/new";
    }
}
