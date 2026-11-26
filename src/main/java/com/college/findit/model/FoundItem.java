package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "found_items")
@Getter
@Setter
public class FoundItem extends Item {

    public enum Status { OPEN, RESERVED, RETURNED, WITHDRAWN }

    @ManyToOne(optional = false)
    private AppUser finder;

    /** Where the item is kept (e.g. "Security Office, Main Gate"). Shown only after admin approval. */
    @Column(nullable = false)
    private String holdingPlace;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;
}
