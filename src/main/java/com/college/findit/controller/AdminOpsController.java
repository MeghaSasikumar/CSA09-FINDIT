package com.college.findit.controller;

import com.college.findit.model.AppUser;
import com.college.findit.model.LostItem;
import com.college.findit.model.FoundItem;
import com.college.findit.repo.FoundItemRepository;
import com.college.findit.repo.LostItemRepository;
import com.college.findit.repo.UserRepository;
import com.college.findit.service.LostFollowUpService;
import com.college.findit.service.MatchService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin tools for "not found" reports and for owners who say the item is not theirs. */
@Controller
@RequestMapping("/admin")
public class AdminOpsController {

    private final UserRepository users;
    private final LostItemRepository lostRepo;
    private final FoundItemRepository foundRepo;
    private final MatchService matchService;
    private final LostFollowUpService followUp;

    public AdminOpsController(UserRepository users, LostItemRepository lostRepo, FoundItemRepository foundRepo,
                              MatchService matchService, LostFollowUpService followUp) {
        this.users = users;
        this.lostRepo = lostRepo;
        this.foundRepo = foundRepo;
        this.matchService = matchService;
        this.followUp = followUp;
    }

    private AppUser current(Authentication auth) {
        return users.findByRollNo(auth.getName()).orElseThrow();
    }

    @GetMapping("/lost")
    public String lostReports(Model model) {
        model.addAttribute("items", lostRepo.findByStatusOrderByCreatedAtAsc(LostItem.Status.OPEN));
        model.addAttribute("days", followUp.getFollowUpDays());
        return "admin/lost";
    }

    @GetMapping("/found")
    public String foundItems(Model model) {
        model.addAttribute("items", foundRepo.findByStatusInOrderByCreatedAtAsc(
                java.util.List.of(FoundItem.Status.OPEN, FoundItem.Status.RESERVED)));
        return "admin/found";
    }

    @PostMapping("/lost/{id}/not-found")
    public String markNotFound(@PathVariable Long id, @RequestParam(required = false) String message,
                               Authentication auth, RedirectAttributes ra) {
        try {
            followUp.markNotFound(id, message, current(auth));
            ra.addFlashAttribute("msg", "Report closed as not found. The owner has been informed.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lost";
    }

    @PostMapping("/lost/followup")
    public String sendFollowUps(RedirectAttributes ra) {
        int n = followUp.sendFollowUps();
        ra.addFlashAttribute("msg", n + " owner(s) were told that their item is not found yet.");
        return "redirect:/admin/lost";
    }

    @PostMapping("/claims/{id}/mismatch")
    public String resolveMismatch(@PathVariable Long id, @RequestParam String decision,
                                  @RequestParam(required = false) String note,
                                  Authentication auth, RedirectAttributes ra) {
        boolean confirmed = decision.equals("confirm");
        matchService.resolveMismatch(id, confirmed, note, current(auth));
        ra.addFlashAttribute("msg", confirmed
                ? "Mismatch confirmed. The found item is open again for other owners."
                : "Report dismissed. The owner has been told the item may be theirs.");
        return "redirect:/admin/claims";
    }
}
