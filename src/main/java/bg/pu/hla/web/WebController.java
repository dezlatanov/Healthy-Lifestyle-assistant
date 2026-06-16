package bg.pu.hla.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import bg.pu.hla.domain.*;
import bg.pu.hla.security.SecurityUtils;
import bg.pu.hla.service.LifestyleService;

import java.util.Arrays;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final LifestyleService lifestyleService;

    @GetMapping("/")
    public String home(Authentication auth, Model model) {
        String username = SecurityUtils.currentUsername(auth);

        model.addAttribute("goals", Arrays.asList(HealthGoal.values()));
        model.addAttribute("activityLevels", Arrays.asList(ActivityLevel.values()));
        model.addAttribute("genders", Arrays.asList(Gender.values()));
        model.addAttribute("consultationTypes", Arrays.stream(ConsultationType.values())
                .filter(t -> t != ConsultationType.CHAT)
                .collect(Collectors.toList()));
        model.addAttribute("habits", lifestyleService.listHabits());
        model.addAttribute("activeUser", username);

        lifestyleService.findUser(username).ifPresent(profile -> {
            model.addAttribute("userProfile", profile);
            model.addAttribute("dailyLogs", lifestyleService.getDailyLogs(username));
            model.addAttribute("chatHistory", lifestyleService.getChatHistory(username));
        });

        return "index";
    }

    @PostMapping("/profile")
    public String saveProfile(Authentication auth,
                              @ModelAttribute UserProfile profile,
                              RedirectAttributes redirect) {
        String username = SecurityUtils.currentUsername(auth);
        profile.setUsername(username);
        lifestyleService.createOrUpdateUser(profile);
        redirect.addFlashAttribute("message", "Профилът е запазен.");
        return "redirect:/";
    }

    @PostMapping("/consult")
    public String consult(Authentication auth,
                          @RequestParam ConsultationType type,
                          @RequestParam(required = false) String query,
                          RedirectAttributes redirect) {
        String username = SecurityUtils.currentUsername(auth);
        try {
            var result = lifestyleService.consult(username, type, query);
            redirect.addFlashAttribute("consultResult", result.get("response"));
            redirect.addFlashAttribute("consultContext", result.get("contextSummary"));
            redirect.addFlashAttribute("consultTips", result.get("tips"));
            redirect.addFlashAttribute("consultItems", result.get("recommendations"));
            redirect.addFlashAttribute("message", "Препоръките са готови.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/log")
    public String saveLog(Authentication auth,
                          @ModelAttribute DailyLog log,
                          RedirectAttributes redirect) {
        String username = SecurityUtils.currentUsername(auth);
        lifestyleService.saveDailyLog(username, log);
        redirect.addFlashAttribute("message", "Дневникът е записан.");
        return "redirect:/";
    }

    @PostMapping("/ontology/food")
    public String addFood(Authentication auth,
                          @RequestParam String foodName,
                          @RequestParam int calories,
                          @RequestParam double protein,
                          RedirectAttributes redirect) {
        String username = SecurityUtils.currentUsername(auth);
        var meal = lifestyleService.addFood(username, foodName, calories, protein);
        redirect.addFlashAttribute("message", "Храната „" + meal.getLabel() + "“ е добавена.");
        return "redirect:/";
    }
}
