package com.college.findit.repo;

import com.college.findit.model.AppUser;
import com.college.findit.model.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FoundItemRepository extends JpaRepository<FoundItem, Long> {
    List<FoundItem> findByFinderOrderByCreatedAtDesc(AppUser finder);
    List<FoundItem> findByStatus(FoundItem.Status status);
    long countByStatus(FoundItem.Status status);
    List<FoundItem> findByStatusInOrderByCreatedAtAsc(java.util.Collection<FoundItem.Status> statuses);
    java.util.Optional<FoundItem> findByPhotoId(Long photoId);
    List<FoundItem> findByStatusAndCreatedAtBefore(FoundItem.Status status, LocalDateTime before);
}
