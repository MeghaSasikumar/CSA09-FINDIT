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
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The heart of FindIt: compares lost and found reports, calculates a match percentage and runs the
 * claim workflow.
 *
 *   Total (100) = Public score (60) + Private-answer score (40)
 *   Public  : category 20 + location 15 + date closeness 15 + keyword overlap 10
 *   Private : 40 x (private answers that match / private details the finder recorded)
 *
 * Claim workflow:
 *   SUGGESTED -> VERIFICATION_REQUESTED -> APPROVED (admin) -> COLLECTED (owner confirms)
 *                                                            -> MISMATCH_REPORTED (owner: "not mine") -> admin decides
 */
@Service
public class MatchService {

    public static final double MIN_TOTAL = 60;
    public static final double MIN_PRIVATE_FRACTION = 0.5;

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "with", "for", "was", "has", "have",
            "its", "this", "that", "from", "near", "are", "very", "one", "not", "any", "lost", "found");

    public record MatchResult(double publicScore, double privateScore, double total) { }

    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchRepository matchRepo;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifier;

    public MatchService(LostItemRepository lostRepo, FoundItemRepository foundRepo, MatchRepository matchRepo,
                        UserRepository users, AuditService audit, NotificationService notifier) {
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchRepo = matchRepo;
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
    }

    // ------------------------------------------------------------------ scoring

    /** Returns the score, or empty if this pair should NOT be suggested. */
    public Optional<MatchResult> evaluate(LostItem lost, FoundItem found) {
        if (!sameText(lost.getCategory(), found.getCategory())) {
            return Optional.empty();
        }

        double pub = 20;                                              // category matched
        if (sameText(lost.getLocation(), found.getLocation())) pub += 15;

        // An item cannot be found before it was lost (1 day of tolerance for typing mistakes).
        long diff = ChronoUnit.DAYS.between(lost.getItemDate(), found.getItemDate());
        if (diff >= -1) {
            long days = Math.abs(diff);
            if (days <= 3) pub += 15;
            else if (days <= 7) pub += 8;
        }

        pub += 10 * jaccard(tokens(lost.getTitle() + " " + lost.getDescription()),
                tokens(found.getTitle() + " " + found.getDescription()));

        String[] claimed = lost.privateAnswers();
        String[] hidden = found.privateAnswers();
        int hiddenCount = 0;
        int correct = 0;
        for (int i = 0; i < hidden.length; i++) {
            if (!isBlank(hidden[i])) {
                hiddenCount++;
                if (answersMatch(claimed[i], hidden[i])) correct++;
            }
        }
        if (hiddenCount == 0) return Optional.empty();

        double fraction = (double) correct / hiddenCount;
        if (fraction < MIN_PRIVATE_FRACTION) return Optional.empty();

        double priv = 40 * fraction;
        double total = pub + priv;
        if (total < MIN_TOTAL) return Optional.empty();

        return Optional.of(new MatchResult(round(pub), round(priv), round(total)));
    }

    /**
     * Compares whole words, not letters, so "500" does not match "1500".
     * Equal, or all words of the shorter answer appear in the longer one, or at least half the words are shared.
     */
    boolean answersMatch(String a, String b) {
        Set<String> x = words(a);
        Set<String> y = words(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        Set<String> small = x.size() <= y.size() ? x : y;
        Set<String> big = (small == x) ? y : x;
        if (String.join("", small).length() >= 3 && big.containsAll(small)) return true;
        return jaccard(x, y) >= 0.5;
    }

    private static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> words(String s) {
        Set<String> result = new LinkedHashSet<>();
        for (String w : normalise(s).split(" ")) {
            if (!w.isEmpty()) result.add(w);
        }
        return result;
    }

    private static Set<String> tokens(String s) {
        Set<String> result = new HashSet<>();
        for (String word : normalise(s).split(" ")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) result.add(word);
        }
        return result;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) common.size() / all.size();
    }

    private static boolean sameText(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }

    // ------------------------------------------------------------------ creating matches

    /** Run when a lost report is saved: compare it with every open found report. */
    @Transactional
    public int runForLost(LostItem lost) {
        int created = 0;
        for (FoundItem found : foundRepo.findByStatus(FoundItem.Status.OPEN)) {
            if (createIfMatch(lost, found)) created++;
        }
        return created;
    }

    /** Run when a found report is saved (or reopened): compare it with every open lost report. */
    @Transactional
    public int runForFound(FoundItem found) {
        int created = 0;
        for (LostItem lost : lostRepo.findByStatus(LostItem.Status.OPEN)) {
            if (createIfMatch(lost, found)) created++;
        }
        return created;
    }

    private boolean createIfMatch(LostItem lost, FoundItem found) {
        // A person cannot "match" with their own found report.
        if (lost.getOwner().getId().equals(found.getFinder().getId())) return false;
        if (matchRepo.existsByLostItemAndFoundItem(lost, found)) return false;

        Optional<MatchResult> result = evaluate(lost, found);
        if (result.isEmpty()) return false;

        MatchRecord m = new MatchRecord();
        m.setLostItem(lost);
        m.setFoundItem(found);
        m.setPublicScore(result.get().publicScore());
        m.setPrivateScore(result.get().privateScore());
        m.setTotalScore(result.get().total());
        matchRepo.save(m);

        notifier.send(lost.getOwner(), "Good news: a possible match (" + Math.round(result.get().total())
                + "%) was found for your lost item '" + lost.getTitle() + "'. Open it to continue.", "/lost/mine");
        return true;
    }

    // ------------------------------------------------------------------ claim workflow

    private static void mustOwn(MatchRecord m, AppUser student) {
        if (!m.getLostItem().getOwner().getId().equals(student.getId())) {
            throw new IllegalStateException("This match does not belong to you.");
        }
    }

    /** Step 1 - the student confirms "this might be mine" and asks the admin to verify. */
    @Transactional
    public void requestVerification(Long matchId, AppUser student) {
        MatchRecord m = matchRepo.findById(matchId).orElseThrow();
        mustOwn(m, student);
        if (m.getStatus() != MatchRecord.Status.SUGGESTED) return;
        if (m.getFoundItem().getStatus() != FoundItem.Status.OPEN
                || m.getLostItem().getStatus() != LostItem.Status.OPEN) {
            throw new IllegalStateException("This found item is not available right now.");
        }
        m.setStatus(MatchRecord.Status.VERIFICATION_REQUESTED);
        matchRepo.save(m);
        audit.log(student.getRollNo(), "VERIFICATION_REQUESTED",
                "Match #" + m.getId() + " (" + m.getTotalScore() + "%) lost item '" + m.getLostItem().getTitle() + "'");
        for (AppUser admin : users.findByRole(AppUser.Role.ADMIN)) {
            notifier.send(admin, student.getRollNo() + " asked you to verify a claim for '"
                    + m.getLostItem().getTitle() + "' (" + Math.round(m.getTotalScore()) + "% match).", "/admin/claims");
        }
    }

    /** Step 2 - admin approves or rejects. Approval reserves the found item for the owner. */
    @Transactional
    public void decide(Long matchId, boolean approve, String note, AppUser admin) {
        MatchRecord m = matchRepo.findById(matchId).orElseThrow();
        if (m.getStatus() != MatchRecord.Status.VERIFICATION_REQUESTED) return;
        FoundItem found = m.getFoundItem();
        LostItem lost = m.getLostItem();

        if (approve && found.getStatus() != FoundItem.Status.OPEN) {
            throw new IllegalStateException("This found item is already reserved for another verified owner.");
        }

        m.setStatus(approve ? MatchRecord.Status.APPROVED : MatchRecord.Status.REJECTED);
        m.setAdminNote(note);
        m.setDecidedBy(admin.getRollNo());
        m.setDecidedAt(LocalDateTime.now());
        matchRepo.save(m);

        if (approve) {
            found.setStatus(FoundItem.Status.RESERVED);
            foundRepo.save(found);
            notifier.send(lost.getOwner(), "Your claim for '" + lost.getTitle() + "' is approved. Collect the item from "
                    + found.getHoldingPlace() + " with your ID card. Check it carefully - if it is NOT your item, "
                    + "report it in the app.", "/lost/mine");
        } else {
            notifier.send(lost.getOwner(), "Your claim for '" + lost.getTitle() + "' could not be verified."
                    + (nz(note).isEmpty() ? "" : " Admin note: " + nz(note)), "/lost/mine");
        }
        audit.log(admin.getRollNo(), approve ? "CLAIM_APPROVED" : "CLAIM_REJECTED",
                "Match #" + m.getId() + " (" + m.getTotalScore() + "%) note: " + (nz(note).isEmpty() ? "-" : nz(note)));
    }

    /** Step 3a - the owner saw the item and confirms it is theirs. */
    @Transactional
    public void confirmCollected(Long matchId, AppUser student) {
        MatchRecord m = matchRepo.findById(matchId).orElseThrow();
        mustOwn(m, student);
        if (m.getStatus() != MatchRecord.Status.APPROVED) return;
        LostItem lost = m.getLostItem();
        FoundItem found = m.getFoundItem();

        m.setStatus(MatchRecord.Status.COLLECTED);
        m.setDecidedAt(LocalDateTime.now());
        matchRepo.save(m);
        lost.setStatus(LostItem.Status.RECOVERED);
        found.setStatus(FoundItem.Status.RETURNED);
        lostRepo.save(lost);
        foundRepo.save(found);

        List<MatchRecord> others = new ArrayList<>(matchRepo.findByLostItem(lost));
        others.addAll(matchRepo.findByFoundItem(found));
        for (MatchRecord o : others) {
            if (!o.getId().equals(m.getId()) && (o.getStatus() == MatchRecord.Status.SUGGESTED
                    || o.getStatus() == MatchRecord.Status.VERIFICATION_REQUESTED)) {
                o.setStatus(MatchRecord.Status.REJECTED);
                o.setAdminNote("Closed automatically: the item was returned to its owner.");
                o.setDecidedAt(LocalDateTime.now());
                matchRepo.save(o);
                if (!o.getLostItem().getOwner().getId().equals(student.getId())) {
                    notifier.send(o.getLostItem().getOwner(), "The found item that matched your report '"
                            + o.getLostItem().getTitle() + "' was returned to its owner. We keep looking for yours.",
                            "/lost/mine");
                }
            }
        }
        notifier.send(found.getFinder(), "Thank you! The item you found ('" + found.getTitle()
                + "') was returned to its owner.", "/found/mine");
        audit.log(student.getRollNo(), "ITEM_COLLECTED", "Match #" + m.getId() + " '" + lost.getTitle() + "' returned");
    }

    /** Step 3b - the owner inspected the item and says it is NOT theirs: report it to the admin. */
    @Transactional
    public void reportMismatch(Long matchId, String reason, AppUser student) {
        MatchRecord m = matchRepo.findById(matchId).orElseThrow();
        mustOwn(m, student);
        if (m.getStatus() != MatchRecord.Status.APPROVED) return;

        String text = nz(reason);
        m.setMismatchReason(text.length() > 480 ? text.substring(0, 480) : text);
        m.setStatus(MatchRecord.Status.MISMATCH_REPORTED);
        matchRepo.save(m);

        audit.log(student.getRollNo(), "MISMATCH_REPORTED", "Match #" + m.getId() + ": " + m.getMismatchReason());
        for (AppUser admin : users.findByRole(AppUser.Role.ADMIN)) {
            notifier.send(admin, student.getRollNo() + " inspected the item for match #" + m.getId()
                    + " and says it is NOT theirs. Please review.", "/admin/claims");
        }
    }

    /** Step 4 - admin reviews the mismatch report. */
    @Transactional
    public void resolveMismatch(Long matchId, boolean confirmed, String note, AppUser admin) {
        MatchRecord m = matchRepo.findById(matchId).orElseThrow();
        if (m.getStatus() != MatchRecord.Status.MISMATCH_REPORTED) return;
        FoundItem found = m.getFoundItem();
        LostItem lost = m.getLostItem();

        m.setDecidedBy(admin.getRollNo());
        m.setDecidedAt(LocalDateTime.now());
        if (confirmed) {
            m.setStatus(MatchRecord.Status.REJECTED);
            m.setAdminNote("The owner reported this is not their item. " + nz(note));
            matchRepo.save(m);
            found.setStatus(FoundItem.Status.OPEN);          // available again for other owners
            foundRepo.save(found);
            notifier.send(lost.getOwner(), "Thank you for reporting. The item was not yours, so we reopened it and "
                    + "will keep looking for '" + lost.getTitle() + "'.", "/lost/mine");
            audit.log(admin.getRollNo(), "MISMATCH_CONFIRMED", "Match #" + m.getId() + " " + nz(note));
            runForFound(found);                               // maybe someone else matches it now
        } else {
            m.setStatus(MatchRecord.Status.APPROVED);
            m.setAdminNote("The admin reviewed your report. " + nz(note));
            matchRepo.save(m);
            notifier.send(lost.getOwner(), "The admin reviewed your report for '" + lost.getTitle()
                    + "' and believes the item may be yours. " + nz(note), "/lost/mine");
            audit.log(admin.getRollNo(), "MISMATCH_DISMISSED", "Match #" + m.getId() + " " + nz(note));
        }
    }
}
