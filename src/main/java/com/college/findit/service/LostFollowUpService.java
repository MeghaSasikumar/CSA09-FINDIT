package com.college.findit.service;

import com.college.findit.model.LostItem;
import com.college.findit.model.AppUser;
import com.college.findit.model.MatchRecord;
import com.college.findit.repo.LostItemRepository;
import com.college.findit.repo.MatchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Tells owners when their lost item has NOT been found. */
@Service
public class LostFollowUpService {

    private static final List<MatchRecord.Status> LIVE = List.of(MatchRecord.Status.SUGGESTED,
            MatchRecord.Status.VERIFICATION_REQUESTED, MatchRecord.Status.APPROVED, MatchRecord.Status.MISMATCH_REPORTED);

    @Value("${findit.followup-days}")
    private int followUpDays;

    private final LostItemRepository lostRepo;
    private final MatchRepository matchRepo;
    private final NotificationService notifier;
    private final AuditService audit;

    public LostFollowUpService(LostItemRepository lostRepo, MatchRepository matchRepo,
                               NotificationService notifier, AuditService audit) {
        this.lostRepo = lostRepo;
        this.matchRepo = matchRepo;
        this.notifier = notifier;
        this.audit = audit;
    }

    public int getFollowUpDays() {
        return followUpDays;
    }

    /** Runs automatically every day (time is set in application.properties). */
    @Scheduled(cron = "${findit.followup-cron}")
    public void runDaily() {
        sendFollowUps();
    }

    /** Reminds owners of old open reports that have no live match. Returns how many were told. */
    @Transactional
    public int sendFollowUps() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(followUpDays);
        int told = 0;
        for (LostItem item : lostRepo.findByStatusAndFollowUpSentFalseAndCreatedAtBefore(LostItem.Status.OPEN, cutoff)) {
            if (matchRepo.countByLostItemAndStatusIn(item, LIVE) > 0) continue;
            notifier.send(item.getOwner(), "We have not found your lost item '" + item.getTitle() + "' yet. "
                    + "Please also check at the security office. Your report stays open and we will notify you "
                    + "here as soon as a possible match appears.", "/lost/mine");
            item.setFollowUpSent(true);
            lostRepo.save(item);
            told++;
        }
        if (told > 0) audit.log("SYSTEM", "NOT_FOUND_REMINDER", told + " owner(s) told that their item is not found yet");
        return told;
    }

    /** Admin closes a report as 'not found' and informs the owner. */
    @Transactional
    public void markNotFound(Long lostId, String message, AppUser admin) {
        LostItem item = lostRepo.findById(lostId).orElseThrow();
        if (item.getStatus() != LostItem.Status.OPEN) return;
        if (matchRepo.countByLostItemAndStatusIn(item, List.of(MatchRecord.Status.APPROVED,
                MatchRecord.Status.MISMATCH_REPORTED)) > 0) {
            throw new IllegalStateException("A claim for this item is in progress. Finish it first.");
        }
        String note = (message == null || message.isBlank())
                ? "The college could not find this item. If you remember anything new, please report it again."
                : message.trim();
        item.setStatus(LostItem.Status.NOT_FOUND);
        item.setClosingNote(note.length() > 480 ? note.substring(0, 480) : note);
        lostRepo.save(item);

        for (MatchRecord m : matchRepo.findByLostItem(item)) {
            if (m.getStatus() == MatchRecord.Status.SUGGESTED || m.getStatus() == MatchRecord.Status.VERIFICATION_REQUESTED) {
                m.setStatus(MatchRecord.Status.REJECTED);
                m.setAdminNote("Closed: the lost report was closed as not found.");
                matchRepo.save(m);
            }
        }
        notifier.send(item.getOwner(), "Your lost item '" + item.getTitle() + "' could not be found. " + item.getClosingNote(),
                "/lost/mine");
        audit.log(admin.getRollNo(), "LOST_MARKED_NOT_FOUND", "'" + item.getTitle() + "' of " + item.getOwner().getRollNo());
    }
}
