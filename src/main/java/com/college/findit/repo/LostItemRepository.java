package com.college.findit.repo;

import com.college.findit.model.AppUser;
import com.college.findit.model.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {
    List<LostItem> findByOwnerOrderByCreatedAtDesc(AppUser owner);
    List<LostItem> findByStatus(LostItem.Status status);
    long countByStatus(LostItem.Status status);
    List<LostItem> findByStatusOrderByCreatedAtAsc(LostItem.Status status);
    List<LostItem> findByStatusAndFollowUpSentFalseAndCreatedAtBefore(LostItem.Status status, java.time.LocalDateTime before);
    java.util.Optional<LostItem> findByPhotoId(Long photoId);
    long countByOwnerAndCreatedAtAfter(AppUser owner, java.time.LocalDateTime since);

    /** Analytics: which campus locations have the most lost-item reports. */
    @Query("select l.location, count(l) from LostItem l group by l.location order by count(l) desc")
    List<Object[]> countByLocation();
}
