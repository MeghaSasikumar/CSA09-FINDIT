package com.college.findit.service;

import com.college.findit.model.AppUser;
import com.college.findit.model.FoundItem;
import com.college.findit.model.LostItem;
import com.college.findit.model.MatchRecord;
import com.college.findit.repo.FoundItemRepository;
import com.college.findit.repo.LostItemRepository;
import com.college.findit.repo.MatchRepository;
import com.college.findit.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Lets a student close their own report (for example "I found it myself"). */
@Service
public class ReportService {

    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchRepository matchRepo;
    private final UserRepository users;
    private final NotificationService notifier;
    private final AuditService audit;

    public ReportService(LostItemRepository lostRepo, FoundItemRepository foundRepo, MatchRepository matchRepo,
                         UserRepository users, NotificationService notifier, AuditService audit) {
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchRepo = matchRepo;
        this.users = users;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional
    public void withdrawLost(Long id, AppUser student) {
        LostItem item = lostRepo.findById(id).orElseThrow();
        if (!item.getOwner().getId().equals(student.getId())) {
            throw new IllegalStateException("This report does not belong to you.");
        }
        if (item.getStatus() != LostItem.Status.OPEN) return;
        if (matchRepo.countByLostItemAndStatusIn(item, List.of(MatchRecord.Status.APPROVED,
                MatchRecord.Status.MISMATCH_REPORTED)) > 0) {
            throw new IllegalStateException("A claim for this item is in progress. Please finish it first.");
        }
        item.setStatus(LostItem.Status.WITHDRAWN);
        item.setClosingNote("Closed by the owner.");
        lostRepo.save(item);
        for (MatchRecord m : matchRepo.findByLostItem(item)) {
            if (m.getStatus() == MatchRecord.Status.SUGGESTED || m.getStatus() == MatchRecord.Status.VERIFICATION_REQUESTED) {
                m.setStatus(MatchRecord.Status.REJECTED);
                m.setAdminNote("Closed: the owner closed the lost report.");
                m.setDecidedAt(LocalDateTime.now());
                matchRepo.save(m);
            }
        }
        audit.log(student.getRollNo(), "LOST_WITHDRAWN", "'" + item.getTitle() + "' closed by its owner");
    }

    @Transactional
    public void withdrawFound(Long id, AppUser student) {
        FoundItem item = foundRepo.findById(id).orElseThrow();
        if (!item.getFinder().getId().equals(student.getId())) {
            throw new IllegalStateException("This report does not belong to you.");
        }
        if (item.getStatus() == FoundItem.Status.RESERVED) {
            throw new IllegalStateException("An owner has been verified for this item. Please speak to the admin.");
        }
        if (item.getStatus() != FoundItem.Status.OPEN) return;

        item.setStatus(FoundItem.Status.WITHDRAWN);
        foundRepo.save(item);
        for (MatchRecord m : matchRepo.findByFoundItem(item)) {
            if (m.getStatus() == MatchRecord.Status.SUGGESTED || m.getStatus() == MatchRecord.Status.VERIFICATION_REQUESTED) {
                m.setStatus(MatchRecord.Status.REJECTED);
                m.setAdminNote("Closed: the finder withdrew the found report.");
                m.setDecidedAt(LocalDateTime.now());
                matchRepo.save(m);
                notifier.send(m.getLostItem().getOwner(), "The found item that matched your report '"
                        + m.getLostItem().getTitle() + "' was withdrawn. We keep looking for yours.", "/lost/mine");
            }
        }
        audit.log(student.getRollNo(), "FOUND_WITHDRAWN", "'" + item.getTitle() + "' withdrawn by the finder");
        for (AppUser admin : users.findByRole(AppUser.Role.ADMIN)) {
            notifier.send(admin, student.getRollNo() + " withdrew the found item '" + item.getTitle()
                    + "'. Check that it is safe at " + item.getHoldingPlace() + ".", "/admin/found");
        }
    }
}
