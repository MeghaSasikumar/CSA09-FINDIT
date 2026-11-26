package com.college.findit.controller;

import com.college.findit.model.AppUser;
import com.college.findit.model.FoundItem;
import com.college.findit.model.LostItem;
import com.college.findit.model.MatchRecord;
import com.college.findit.repo.*;
import com.college.findit.service.AdminService;
import com.college.findit.service.MatchService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** Admin-only pages (protected by SecurityConfig: /admin/** needs ROLE_ADMIN). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository users;
    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchRepository matchRepo;
    private final AuditLogRepository auditRepo;
    private final MatchService matchService;
    private final AdminService adminService;

    public AdminController(UserRepository users, LostItemRepository lostRepo, FoundItemRepository foundRepo,
                           MatchRepository matchRepo, AuditLogRepository auditRepo,
                           MatchService matchService, AdminService adminService) {
        this.users = users;
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchRepo = matchRepo;
        this.auditRepo = auditRepo;
        this.matchService = matchService;
        this.adminService = adminService;
    }

    private AppUser current(Authentication auth) {
        return users.findByRollNo(auth.getName()).orElseThrow();
    }

    @GetMapping
    public String dashboard(Model model) {
        List<FoundItem> stale = foundRepo.findByStatusAndCreatedAtBefore(
                FoundItem.Status.OPEN, LocalDateTime.now().minusDays(30));
        model.addAttribute("userCount", users.count());
        model.addAttribute("openLost", lostRepo.countByStatus(LostItem.Status.OPEN));
        model.addAttribute("openFound", foundRepo.countByStatus(FoundItem.Status.OPEN));
        model.addAttribute("pending", matchRepo.countByStatus(MatchRecord.Status.VERIFICATION_REQUESTED));
        model.addAttribute("recovered", lostRepo.countByStatus(LostItem.Status.RECOVERED));
        model.addAttribute("stale", stale);
        model.addAttribute("locationStats", lostRepo.countByLocation());
        model.addAttribute("mismatchCount", matchRepo.countByStatus(MatchRecord.Status.MISMATCH_REPORTED));
        model.addAttribute("pendingList", matchRepo.findByStatusOrderByCreatedAtAsc(MatchRecord.Status.VERIFICATION_REQUESTED));
        model.addAttribute("recent", auditRepo.findTop8ByOrderByCreatedAtDesc());
        return "admin/dashboard";
    }

    // ------------------------------------------------------------ claim verification

    @GetMapping("/claims")
    public String claims(Model model) {
        model.addAttribute("pending", matchRepo.findByStatusOrderByCreatedAtAsc(MatchRecord.Status.VERIFICATION_REQUESTED));
        model.addAttribute("history", matchRepo.findTop50ByStatusInOrderByDecidedAtDesc(
                List.of(MatchRecord.Status.APPROVED, MatchRecord.Status.COLLECTED, MatchRecord.Status.REJECTED)));
        model.addAttribute("mismatches", matchRepo.findByStatusOrderByCreatedAtAsc(MatchRecord.Status.MISMATCH_REPORTED));
        return "admin/claims";
    }

    @PostMapping("/claims/{id}")
    public String decide(@PathVariable Long id, @RequestParam String decision,
                         @RequestParam(required = false) String note,
                         Authentication auth, RedirectAttributes ra) {
        boolean approve = decision.equals("approve");
        try {
            matchService.decide(id, approve, note, current(auth));
            ra.addFlashAttribute("msg", approve
                    ? "Claim approved. The owner has been told where to collect the item."
                    : "Claim rejected. The owner has been informed.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/claims";
    }

    // ------------------------------------------------------------ users and batches

    @GetMapping("/users")
    public String userList(Model model) {
        List<AppUser> all = users.findAllByOrderByRoleAscRollNoAsc();
        model.addAttribute("users", all);
        model.addAttribute("batches", all.stream().map(AppUser::getBatchYear)
                .filter(java.util.Objects::nonNull).distinct().sorted().toList());
        return "admin/users";
    }

    // The CSRF token is sent in the URL for this multipart upload (see template).
    @PostMapping("/users/import")
    public String importCsv(@RequestParam("file") MultipartFile file, Authentication auth,
                            RedirectAttributes ra) throws IOException {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please choose a CSV file.");
            return "redirect:/admin/users";
        }
        AdminService.ImportResult r = adminService.importCsv(file, auth.getName());
        ra.addFlashAttribute("msg", "Imported " + r.added() + " students, skipped " + r.skipped()
                + " that already exist." + (r.errors().isEmpty() ? "" : " Problems: " + String.join("; ", r.errors())));
        return "redirect:/admin/users";
    }

    @PostMapping("/users/add")
    public String addStudent(@RequestParam String rollNo, @RequestParam String name, @RequestParam String email,
                             @RequestParam String department, @RequestParam Integer batchYear,
                             Authentication auth, RedirectAttributes ra) {
        if (!adminService.isCollegeEmail(email)) {
            ra.addFlashAttribute("error", "Only college mail ids ending with @" + adminService.getAllowedDomain() + " are allowed.");
            return "redirect:/admin/users";
        }
        boolean ok = adminService.addStudent(rollNo, name, email, department, batchYear);
        if (ok) ra.addFlashAttribute("msg", "Student added. Their first password is their roll number.");
        else ra.addFlashAttribute("error", "That roll number or email already exists.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/deactivate-batch")
    public String deactivateBatch(@RequestParam Integer batchYear, Authentication auth, RedirectAttributes ra) {
        int n = adminService.deactivateBatch(batchYear, auth.getName());
        ra.addFlashAttribute("msg", n + " students of batch " + batchYear + " were marked as alumni and can no longer log in.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset")
    public String resetPassword(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        adminService.resetPassword(id, auth.getName());
        ra.addFlashAttribute("msg", "Password reset to the roll number. The user must change it at next login.");
        return "redirect:/admin/users";
    }

    // ------------------------------------------------------------ audit

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("logs", auditRepo.findTop200ByOrderByCreatedAtDesc());
        return "admin/audit";
    }
}
