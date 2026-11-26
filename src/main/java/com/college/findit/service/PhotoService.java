package com.college.findit.service;

import com.college.findit.model.AppUser;
import com.college.findit.model.FoundItem;
import com.college.findit.model.ItemPhoto;
import com.college.findit.model.LostItem;
import com.college.findit.model.MatchRecord;
import com.college.findit.repo.FoundItemRepository;
import com.college.findit.repo.ItemPhotoRepository;
import com.college.findit.repo.LostItemRepository;
import com.college.findit.repo.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Saves uploaded photos and decides who is allowed to look at them. */
@Service
public class PhotoService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final ItemPhotoRepository photos;
    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchRepository matchRepo;

    public PhotoService(ItemPhotoRepository photos, LostItemRepository lostRepo,
                        FoundItemRepository foundRepo, MatchRepository matchRepo) {
        this.photos = photos;
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchRepo = matchRepo;
    }

    /** Returns the new photo id, or null when no file was chosen. Throws IllegalArgumentException for bad files. */
    public Long save(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("The photo must be smaller than 2 MB.");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("The photo could not be read. Please try again.");
        }
        String type = detectType(data);
        if (type == null) throw new IllegalArgumentException("Only JPG, PNG or WEBP photos are allowed.");

        ItemPhoto p = new ItemPhoto();
        p.setContentType(type);
        p.setData(data);
        return photos.save(p).getId();
    }

    /** Checks the first bytes of the file, so a renamed .exe cannot pass as a photo. */
    private static String detectType(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }

    /**
     * Privacy rules:
     *  - admin can see every photo
     *  - a lost-item photo: only its owner
     *  - a found-item photo: its finder, or the owner of a lost item whose claim the admin has approved
     */
    public boolean canView(Long photoId, AppUser viewer) {
        if (viewer.getRole() == AppUser.Role.ADMIN) return true;

        Optional<LostItem> lost = lostRepo.findByPhotoId(photoId);
        if (lost.isPresent()) return lost.get().getOwner().getId().equals(viewer.getId());

        Optional<FoundItem> found = foundRepo.findByPhotoId(photoId);
        if (found.isPresent()) {
            if (found.get().getFinder().getId().equals(viewer.getId())) return true;
            return matchRepo.existsByFoundItemAndLostItem_OwnerAndStatusIn(found.get(), viewer,
                    List.of(MatchRecord.Status.APPROVED, MatchRecord.Status.COLLECTED, MatchRecord.Status.MISMATCH_REPORTED));
        }
        return false;
    }
}
