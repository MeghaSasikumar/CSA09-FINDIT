package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A person who can log in: a Student or an Admin (role-based access). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class AppUser {

    public enum Role { STUDENT, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String rollNo;          // used as the login username (stored in CAPITALS)

    private String name;
    private String email;
    private String department;
    private Integer batchYear;      // e.g. 2026 - used for bulk import and graduation clean-up

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.STUDENT;

    private boolean active = true;                 // false = alumni / graduated batch
    private boolean mustChangePassword = true;     // forced on first login
}
