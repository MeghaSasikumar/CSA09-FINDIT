package com.college.findit.service;

import com.college.findit.model.AppUser;
import com.college.findit.model.Notification;
import com.college.findit.repo.NotificationRepository;
import org.springframework.stereotype.Service;

/** Creates in-app notifications (shown under the bell in the menu bar). */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void send(AppUser user, String message, String link) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message.length() > 480 ? message.substring(0, 480) : message);
        n.setLink(link);
        repo.save(n);
    }
}
