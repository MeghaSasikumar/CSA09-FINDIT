package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A suggested match between one lost report and one found report, with its percentage. */
@Entity
@Table(name = "matches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lost_item_id", "found_item_id"}))
@Getter
@Setter
public class MatchRecord {

    /** APPROVED = admin verified, owner must inspect/collect; COLLECTED = owner confirmed; MISMATCH_REPORTED = owner says it is not theirs. */
    public enum Status { SUGGESTED, VERIFICATION_REQUESTED, APPROVED, COLLECTED, MISMATCH_REPORTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private LostItem lostItem;

    @ManyToOne(optional = false)
    private FoundItem foundItem;

    private double publicScore;     // out of 60
    private double privateScore;    // out of 40
    private double totalScore;      // out of 100 - the match percentage

    @Enumerated(EnumType.STRING)
    private Status status = Status.SUGGESTED;

    @Column(length = 500)
    private String adminNote;
    @Column(length = 500)
    private String mismatchReason;   // why the owner says the item is not theirs
    private String decidedBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime decidedAt;
}
