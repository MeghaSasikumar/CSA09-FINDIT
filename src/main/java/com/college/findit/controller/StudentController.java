package com.college.findit.controller;

import com.college.findit.model.*;
import com.college.findit.repo.*;
import com.college.findit.service.AuditService;
import com.college.findit.service.MatchService;
import com.college.findit.service.PhotoService;
import com.college.findit.service.QuestionBank;
import com.college.findit.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a logged-in student can do. */
@Controller
public class StudentController {

    private static final int MAX_LOST_PER_DAY = 5;

    private final UserRepository users;
    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchRepository matchRepo;
    private final MatchService matchService;
    private final PhotoService photoService;
    private final AuditService audit;
    private final ReportService reports;

    public StudentController(UserRepository users, LostItemRepository lostRepo, FoundItemRepository foundRepo,
                             MatchRepository matchRepo, MatchService matchService, PhotoService photoService,
                             AuditService audit, ReportService reports) {
        this.users = users;
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchRepo = matchRepo;
        this.matchService = matchService;
        this.photoService = photoService;
        this.audit = audit;
        this.reports = reports;
    }

    private AppUser current(Authentication auth) {
        return users.findByRollNo(auth.getName()).orElseThrow();
    }

    private void prepareForm(Model model, String kind) {
        model.addAttribute("kind", kind);
        model.addAttribute("categories", QuestionBank.categories());
        model.addAttribute("locations", QuestionBank.LOCATIONS);
        model.addAttribute("questionMap", QuestionBank.all());
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) return "redirect:/admin";
        AppUser u = current(auth);
        List<LostItem> lost = lostRepo.findByOwnerOrderByCreatedAtDesc(u);
        long suggestions = 0;
        for (LostItem l : lost) {
            suggestions += matchRepo.findByLostItem(l).stream()
                    .filter(m -> m.getStatus() == MatchRecord.Status.SUGGESTED).count();
        }
        model.addAttribute("user", u);
        model.addAttribute("lostCount", lost.size());
        model.addAttribute("foundCount", foundRepo.findByFinderOrderByCreatedAtDesc(u).size());
        model.addAttribute("suggestions", suggestions);
        return "dashboard";
    }

    // ---------------------------------------------------------------- report LOST

    @GetMapping("/lost/new")
    public String lostForm(Model model) {
        prepareForm(model, "lost");
        model.addAttribute("form", new ItemForm());
        return "item-form";
    }

    @PostMapping("/lost")
    public String saveLost(@Valid @ModelAttribute("form") ItemForm form, BindingResult br,
                           @RequestParam(value = "photo", required = false) MultipartFile photo,
                           Authentication auth, Model model, RedirectAttributes ra) {
        AppUser u = current(auth);
        if (form.noPrivateDetails()) {
            br.reject("private", "Answer at least one private question - it is how we verify the real owner.");
        }
        // Anti-guessing rule: someone cannot keep re-submitting lost reports to try different answers.
        if (lostRepo.countByOwnerAndCreatedAtAfter(u, LocalDateTime.now().minusHours(24)) >= MAX_LOST_PER_DAY) {
            br.reject("limit", "You can report at most " + MAX_LOST_PER_DAY + " lost items in 24 hours.");
        }
        Long photoId = null;
        if (!br.hasErrors()) {
            try {
                photoId = photoService.save(photo);
            } catch (IllegalArgumentException e) {
                br.reject("photo", e.getMessage());
            }
        }
        if (br.hasErrors()) {
            prepareForm(model, "lost");
            return "item-form";
        }

        LostItem item = new LostItem();
        form.copyTo(item);
        item.setOwner(u);
        item.setPhotoId(photoId);
        lostRepo.save(item);
        audit.log(u.getRollNo(), "LOST_REPORTED", "'" + item.getTitle() + "' at " + item.getLocation());

        int n = matchService.runForLost(item);
        ra.addFlashAttribute("msg", n == 0
                ? "Lost report saved. We will notify you when a matching found item appears, or if it cannot be found."
                : "Lost report saved. " + n + " possible match(es) found - see below!");
        return "redirect:/lost/mine";
    }

    @GetMapping("/lost/mine")
    public String myLost(Authentication auth, Model model) {
        List<LostItem> items = lostRepo.findByOwnerOrderByCreatedAtDesc(current(auth));
        Map<Long, List<MatchRecord>> matches = new LinkedHashMap<>();
        for (LostItem i : items) {
            matches.put(i.getId(), matchRepo.findByLostItemOrderByTotalScoreDesc(i));
        }
        model.addAttribute("items", items);
        model.addAttribute("matches", matches);
        return "my-lost";
    }

    @PostMapping("/matches/{id}/verify")
    public String requestVerification(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            matchService.requestVerification(id, current(auth));
            ra.addFlashAttribute("msg", "Verification requested. The admin will confirm it and you will be notified.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lost/mine";
    }

    /** The owner looked at the item and it IS theirs. */
    @PostMapping("/matches/{id}/collected")
    public String collected(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            matchService.confirmCollected(id, current(auth));
            ra.addFlashAttribute("msg", "Great! We are glad you got your item back.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lost/mine";
    }

    /** The owner looked at the item and it is NOT theirs: report it to the admin. */
    @PostMapping("/matches/{id}/mismatch")
    public String mismatch(@PathVariable Long id, @RequestParam String reason,
                           Authentication auth, RedirectAttributes ra) {
        if (reason == null || reason.isBlank()) {
            ra.addFlashAttribute("error", "Please say what was different, so the admin can check.");
            return "redirect:/lost/mine";
        }
        try {
            matchService.reportMismatch(id, reason, current(auth));
            ra.addFlashAttribute("msg", "Reported. The admin will review it and we will keep searching for your item.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lost/mine";
    }

    // ---------------------------------------------------------------- report FOUND

    @GetMapping("/found/new")
    public String foundForm(Model model) {
        prepareForm(model, "found");
        model.addAttribute("form", new ItemForm());
        return "item-form";
    }

    @PostMapping("/found")
    public String saveFound(@Valid @ModelAttribute("form") ItemForm form, BindingResult br,
                            @RequestParam(value = "photo", required = false) MultipartFile photo,
                            Authentication auth, Model model, RedirectAttributes ra) {
        if (form.noPrivateDetails()) {
            br.reject("private", "Record at least one hidden detail - it is used to verify the real owner.");
        }
        if (form.getHoldingPlace() == null || form.getHoldingPlace().isBlank()) {
            br.rejectValue("holdingPlace", "required", "Say where the item is being kept");
        }
        Long photoId = null;
        if (!br.hasErrors()) {
            try {
                photoId = photoService.save(photo);
            } catch (IllegalArgumentException e) {
                br.reject("photo", e.getMessage());
            }
        }
        if (br.hasErrors()) {
            prepareForm(model, "found");
            return "item-form";
        }
        AppUser u = current(auth);
        FoundItem item = new FoundItem();
        form.copyTo(item);
        item.setFinder(u);
        item.setHoldingPlace(form.getHoldingPlace().trim());
        item.setPhotoId(photoId);
        foundRepo.save(item);
        audit.log(u.getRollNo(), "FOUND_REPORTED", "'" + item.getTitle() + "' at " + item.getLocation());

        int n = matchService.runForFound(item);
        ra.addFlashAttribute("msg", "Found report saved. " + n + " possible owner(s) matched. Thank you for helping!");
        return "redirect:/found/mine";
    }

    @GetMapping("/found/mine")
    public String myFound(Authentication auth, Model model) {
        model.addAttribute("items", foundRepo.findByFinderOrderByCreatedAtDesc(current(auth)));
        return "my-found";
    }

    // ---------------------------------------------------------------- close my own report

    @PostMapping("/lost/{id}/withdraw")
    public String withdrawLost(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            reports.withdrawLost(id, current(auth));
            ra.addFlashAttribute("msg", "Report closed. We are glad you found it!");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lost/mine";
    }

    @PostMapping("/found/{id}/withdraw")
    public String withdrawFound(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            reports.withdrawFound(id, current(auth));
            ra.addFlashAttribute("msg", "Found report withdrawn.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/found/mine";
    }
}
