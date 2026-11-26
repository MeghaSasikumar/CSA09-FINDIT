package com.college.findit.controller;

import com.college.findit.model.Item;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Data typed into the "report lost" / "report found" page. */
@Getter
@Setter
public class ItemForm {

    @NotBlank(message = "Choose a category")
    private String category;

    @NotBlank(message = "Enter a short title")
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @NotBlank(message = "Choose a location")
    private String location;

    @NotNull(message = "Choose a date")
    @PastOrPresent(message = "Date cannot be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate itemDate;

    @Size(max = 300)
    private String private1;
    @Size(max = 300)
    private String private2;
    @Size(max = 300)
    private String private3;

    @Size(max = 200)
    private String holdingPlace;     // found items only

    public boolean noPrivateDetails() {
        return isBlank(private1) && isBlank(private2) && isBlank(private3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Copies the form values into a LostItem or FoundItem. */
    public void copyTo(Item item) {
        item.setCategory(category);
        item.setTitle(title.trim());
        item.setDescription(description == null ? "" : description.trim());
        item.setLocation(location);
        item.setItemDate(itemDate);
        item.setPrivate1(private1 == null ? null : private1.trim());
        item.setPrivate2(private2 == null ? null : private2.trim());
        item.setPrivate3(private3 == null ? null : private3.trim());
    }
}
