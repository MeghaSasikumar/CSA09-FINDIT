package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lost_items")
@Getter
@Setter
public class LostItem extends Item {

    public enum Status { OPEN, RECOVERED, NOT_FOUND, WITHDRAWN }

    @ManyToOne(optional = false)
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    /** Message shown to the owner when the admin closes the report as 'not found'. */
    @Column(length = 500)
    private String closingNote;

    /** True once the 'we have not found it yet' reminder was sent to the owner. */
    @Column(columnDefinition = "boolean default false")
    private Boolean followUpSent = false;
}
