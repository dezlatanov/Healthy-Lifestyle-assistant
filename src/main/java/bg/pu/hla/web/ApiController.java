package bg.pu.hla.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import bg.pu.hla.domain.*;
import bg.pu.hla.service.LifestyleService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final LifestyleService lifestyleService;

    @PostMapping("/users")
    public UserProfile createUser(@RequestBody UserProfile profile) {
        return lifestyleService.createOrUpdateUser(profile);
    }

    @GetMapping("/users/{username}")
    public UserProfile getUser(@PathVariable String username) {
        return lifestyleService.getUser(username);
    }

    @PostMapping("/users/{username}/logs")
    public DailyLog saveLog(@PathVariable String username, @RequestBody DailyLog log) {
        return lifestyleService.saveDailyLog(username, log);
    }

    @GetMapping("/users/{username}/logs")
    public List<DailyLog> getLogs(@PathVariable String username) {
        return lifestyleService.getDailyLogs(username);
    }

    @PostMapping("/users/{username}/consult")
    public Map<String, Object> consult(@PathVariable String username,
                                       @RequestBody ConsultRequest request) {
        return lifestyleService.consult(username, request.type(), request.query());
    }

    @GetMapping("/users/{username}/consultations")
    public List<ConsultationRequest> consultations(@PathVariable String username) {
        return lifestyleService.getConsultations(username);
    }

    @GetMapping("/users/{username}/agent-messages")
    public List<AgentMessageLog> agentMessages(@PathVariable String username) {
        return lifestyleService.getAgentMessages(username);
    }

    @GetMapping("/agent-messages")
    public List<AgentMessageLog> allAgentMessages() {
        return lifestyleService.getAllAgentMessages();
    }

    @PostMapping("/users/{username}/ontology/foods")
    public ResponseEntity<?> addFood(@PathVariable String username, @RequestBody AddFoodRequest request) {
        return ResponseEntity.ok(lifestyleService.addFood(
                username, request.name(), request.calories(), request.protein()));
    }

    @PostMapping("/users/{username}/habits/{habitId}")
    public ResponseEntity<Void> linkHabit(@PathVariable String username, @PathVariable String habitId) {
        lifestyleService.linkHabit(username, habitId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ontology/habits")
    public List<?> listHabits() {
        return lifestyleService.listHabits();
    }

    @PostMapping("/users/{username}/chat")
    public Map<String, Object> chat(@PathVariable String username, @RequestBody ChatRequest request) {
        boolean viaAgents = request.viaAgents() == null || request.viaAgents();
        if (request.mode() != null && !viaAgents) {
            return lifestyleService.chatDirect(username, request.message(), request.mode());
        }
        return lifestyleService.chat(username, request.message(), viaAgents);
    }

    @GetMapping("/users/{username}/chat/history")
    public List<Map<String, Object>> chatHistory(@PathVariable String username) {
        return lifestyleService.getChatHistory(username);
    }

    @GetMapping("/users/{username}/evaluation/compare")
    public Map<String, Object> evaluate(@PathVariable String username,
                                        @RequestParam String query) {
        return lifestyleService.evaluateModes(username, query);
    }

    @GetMapping("/users/{username}/evaluation/benchmark")
    public Map<String, Object> benchmark(@PathVariable String username) {
        return lifestyleService.runEvaluationBenchmark(username);
    }

    @GetMapping("/ontology/stats")
    public Map<String, Long> ontologyStats() {
        return Map.of("statements", lifestyleService.ontologySize());
    }

    public record ConsultRequest(ConsultationType type, String query) {}
    public record AddFoodRequest(String name, int calories, double protein) {}
    public record ChatRequest(String message, ChatMode mode, Boolean viaAgents) {}
}
