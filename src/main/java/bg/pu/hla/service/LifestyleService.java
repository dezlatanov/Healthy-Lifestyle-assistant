package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.pu.hla.agent.AgentGateway;
import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyRecommendation;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LifestyleService {

    private final UserProfileRepository userRepo;
    private final DailyLogRepository dailyLogRepo;
    private final ConsultationRequestRepository consultationRepo;
    private final AgentMessageLogRepository messageLogRepo;
    private final OntologyService ontologyService;
    private final AgentGateway agentGateway;

    @Transactional
    public UserProfile createOrUpdateUser(UserProfile profile) {
        UserProfile saved = userRepo.findByUsername(profile.getUsername())
                .map(existing -> mergeProfile(existing, profile))
                .orElseGet(() -> {
                    if (profile.getGoal() == null) profile.setGoal(HealthGoal.MAINTENANCE);
                    if (profile.getActivityLevel() == null) profile.setActivityLevel(ActivityLevel.MODERATE);
                    return userRepo.save(profile);
                });

        ontologyService.addPersonInstance(
                saved.getUsername(),
                saved.getGoal(),
                saved.getActivityLevel()
        );
        return saved;
    }

    public UserProfile getUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    public Optional<UserProfile> findUser(String username) {
        return userRepo.findByUsername(username);
    }

    @Transactional
    public DailyLog saveDailyLog(String username, DailyLog log) {
        UserProfile user = getUser(username);
        log.setUser(user);
        if (log.getLogDate() == null) {
            log.setLogDate(LocalDate.now());
        }
        return dailyLogRepo.save(log);
    }

    public List<DailyLog> getDailyLogs(String username) {
        return dailyLogRepo.findByUserOrderByLogDateDesc(getUser(username));
    }

    public Map<String, Object> consult(String username, ConsultationType type, String query) {
        UserProfile user = getUser(username);
        return agentGateway.sendConsultation(user, type, query);
    }

    public List<ConsultationRequest> getConsultations(String username) {
        return consultationRepo.findTop20ByUserOrderByCreatedAtDesc(getUser(username));
    }

    public List<AgentMessageLog> getAgentMessages(String username) {
        return messageLogRepo.findTop50ByUserOrderByTimestampDesc(getUser(username));
    }

    public List<AgentMessageLog> getAllAgentMessages() {
        return messageLogRepo.findTop100ByOrderByTimestampDesc();
    }

    public OntologyRecommendation addFood(String username, String nameBg, int calories, double protein) {
        UserProfile user = getUser(username);
        return ontologyService.addCustomFoodWithMeal(nameBg, calories, protein, user.getGoal());
    }

    public void linkHabit(String username, String habitId) {
        ontologyService.linkPersonToHabit(username, habitId);
    }

    public List<OntologyRecommendation> listHabits() {
        return ontologyService.listHabits();
    }

    public long ontologySize() {
        return ontologyService.statementCount();
    }

    public long ontologyBaseSize() {
        return ontologyService.baseStatementCount();
    }

    private UserProfile mergeProfile(UserProfile existing, UserProfile incoming) {
        if (incoming.getDisplayName() != null) existing.setDisplayName(incoming.getDisplayName());
        if (incoming.getAge() != null) existing.setAge(incoming.getAge());
        if (incoming.getWeightKg() != null) existing.setWeightKg(incoming.getWeightKg());
        if (incoming.getHeightCm() != null) existing.setHeightCm(incoming.getHeightCm());
        if (incoming.getGoal() != null) existing.setGoal(incoming.getGoal());
        if (incoming.getActivityLevel() != null) existing.setActivityLevel(incoming.getActivityLevel());
        return userRepo.save(existing);
    }
}
