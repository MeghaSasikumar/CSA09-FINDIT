package com.college.findit.repo;

import com.college.findit.model.FoundItem;
import com.college.findit.model.LostItem;
import com.college.findit.model.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchRepository extends JpaRepository<MatchRecord, Long> {
    boolean existsByLostItemAndFoundItem(LostItem lostItem, FoundItem foundItem);
    List<MatchRecord> findByLostItemOrderByTotalScoreDesc(LostItem lostItem);
    List<MatchRecord> findByLostItem(LostItem lostItem);
    List<MatchRecord> findByFoundItem(FoundItem foundItem);
    List<MatchRecord> findByStatusOrderByCreatedAtAsc(MatchRecord.Status status);
    List<MatchRecord> findTop50ByStatusInOrderByDecidedAtDesc(Collection<MatchRecord.Status> statuses);
    long countByStatus(MatchRecord.Status status);
    long countByLostItemAndStatusIn(LostItem lostItem, Collection<MatchRecord.Status> statuses);
    boolean existsByFoundItemAndLostItem_OwnerAndStatusIn(FoundItem foundItem, com.college.findit.model.AppUser owner,
                                                          Collection<MatchRecord.Status> statuses);
}
