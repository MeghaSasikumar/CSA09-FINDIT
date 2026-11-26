package com.college.findit.repo;

import com.college.findit.model.AppUser;
import com.college.findit.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserOrderByCreatedAtDesc(AppUser user);
    long countByUserAndSeenFalse(AppUser user);

    @Modifying
    @Transactional
    @Query("update Notification n set n.seen = true where n.user = :user and n.seen = false")
    void markAllSeen(@Param("user") AppUser user);
}
