package com.college.findit.controller;

import com.college.findit.model.AppUser;
import com.college.findit.model.Notification;
import com.college.findit.repo.NotificationRepository;
import com.college.findit.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class NotificationController {

    private final UserRepository users;
    private final NotificationRepository notifications;

    public NotificationController(UserRepository users, NotificationRepository notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public String list(Authentication auth, Model model) {
        AppUser u = users.findByRollNo(auth.getName()).orElseThrow();
        List<Notification> items = notifications.findTop50ByUserOrderByCreatedAtDesc(u);
        notifications.markAllSeen(u);                 // the list above still shows which ones were new
        model.addAttribute("items", items);
        model.addAttribute("unreadCount", 0L);
        return "notifications";
    }
}
