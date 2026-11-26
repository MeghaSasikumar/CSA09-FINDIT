package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A message shown to a user inside the app (the bell in the menu bar). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AppUser user;

    @Column(length = 500, nullable = false)
    private String message;

    private String link;                 // page to open when clicked, e.g. /lost/mine
    private boolean seen = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
