package com.college.findit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A photo stored in the database (the cloud host's disk is temporary, the database is not). */
@Entity
@Table(name = "item_photos")
@Getter
@Setter
public class ItemPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(length = 2_500_000)
    private byte[] data;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
