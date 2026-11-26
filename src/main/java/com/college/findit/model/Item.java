package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Base class for LostItem and FoundItem (inheritance).
 * private1..3 hold the PRIVATE details: for a found item they are the hidden facts recorded by the
 * finder; for a lost item they are the owner's answers to the same questions.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private LocalDate itemDate;      // date lost / date found

    @Column(length = 300)
    private String private1;
    @Column(length = 300)
    private String private2;
    @Column(length = 300)
    private String private3;

    /** Id of the uploaded photo (see ItemPhoto), or null when no photo was added. */
    private Long photoId;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** The three private details as an array, so the matcher can loop over them. */
    public String[] privateAnswers() {
        return new String[]{private1, private2, private3};
    }
}
