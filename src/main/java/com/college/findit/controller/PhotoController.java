package com.college.findit.controller;

import com.college.findit.model.AppUser;
import com.college.findit.model.ItemPhoto;
import com.college.findit.repo.ItemPhotoRepository;
import com.college.findit.repo.UserRepository;
import com.college.findit.service.PhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

/** Serves a photo only to people who are allowed to see it (see PhotoService.canView). */
@Controller
public class PhotoController {

    private final UserRepository users;
    private final ItemPhotoRepository photos;
    private final PhotoService photoService;

    public PhotoController(UserRepository users, ItemPhotoRepository photos, PhotoService photoService) {
        this.users = users;
        this.photos = photos;
        this.photoService = photoService;
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, Authentication auth) {
        AppUser viewer = users.findByRollNo(auth.getName()).orElseThrow();
        ItemPhoto p = photos.findById(id).orElse(null);
        if (p == null || !photoService.canView(id, viewer)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(p.getData());
    }
}
