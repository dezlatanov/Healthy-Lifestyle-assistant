package bg.pu.hla.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import bg.pu.hla.domain.*;
import bg.pu.hla.service.LifestyleService;

import java.util.Arrays;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final LifestyleService lifestyleService;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String user, Model model) {
        model.addAttribute("goals", Arrays.asList(HealthGoal.values()));
        model.addAttribute("activityLevels", Arrays.asList(ActivityLevel.values()));
        model.addAttribute("consultationTypes", Arrays.asList(ConsultationType.values()));
        model.addAttribute("ontologyStatements", lifestyleService.ontologySize());
        model.addAttribute("ontologyBaseStatements", lifestyleService.ontologyBaseSize());
        model.addAttribute("habits", lifestyleService.listHabits());

        String username = user != null && !user.isBlank() ? user : "demo";
        model.addAttribute("activeUser", username);
        lifestyleService.findUser(username).ifPresent(profile -> {
            model.addAttribute("userProfile", profile);
            model.addAttribute("dailyLogs", lifestyleService.getDailyLogs(username));
        });

        return "index";
    }

    @PostMapping("/profile")
    public String saveProfile(@ModelAttribute UserProfile profile, RedirectAttributes redirect) {
        lifestyleService.createOrUpdateUser(profile);
        redirect.addFlashAttribute("message", "Profile saved and synced to ontology.");
        return "redirect:/?user=" + profile.getUsername();
    }

    @PostMapping("/consult")
    public String consult(@RequestParam String username,
                          @RequestParam ConsultationType type,
                          @RequestParam(required = false) String query,
                          RedirectAttributes redirect) {
        try {
            var result = lifestyleService.consult(username, type, query);
            redirect.addFlashAttribute("consultResult", result.get("response"));
            redirect.addFlashAttribute("consultContext", result.get("contextSummary"));
            redirect.addFlashAttribute("consultTips", result.get("tips"));
            redirect.addFlashAttribute("consultItems", result.get("recommendations"));
            redirect.addFlashAttribute("consultAgent", result.get("agent"));
            redirect.addFlashAttribute("message", "Agent consultation completed via FIPA ACL.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/?user=" + username;
    }

    @PostMapping("/log")
    public String saveLog(@RequestParam String username,
                          @ModelAttribute DailyLog log,
                          RedirectAttributes redirect) {
        lifestyleService.saveDailyLog(username, log);
        redirect.addFlashAttribute("message", "Daily log saved to database.");
        return "redirect:/?user=" + username;
    }

    @PostMapping("/ontology/food")
    public String addFood(@RequestParam String username,
                          @RequestParam String foodName,
                          @RequestParam int calories,
                          @RequestParam double protein,
                          RedirectAttributes redirect) {
        var meal = lifestyleService.addFood(username, foodName, calories, protein);
        redirect.addFlashAttribute("message",
                "Custom meal \"" + meal.getLabel() + "\" added for your goal. Ask Nutrition Agent to see it in recommendations.");
        return "redirect:/?user=" + username;
    }
}
