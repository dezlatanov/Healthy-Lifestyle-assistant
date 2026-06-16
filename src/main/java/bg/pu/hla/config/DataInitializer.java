package bg.pu.hla.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import bg.pu.hla.domain.*;
import bg.pu.hla.repository.UserProfileRepository;
import bg.pu.hla.service.AuthService;
import bg.pu.hla.service.LifestyleService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserProfileRepository userRepo;
    private final AuthService authService;
    private final LifestyleService lifestyleService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread initThread = new Thread(this::seedAll, "demo-data-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    private void seedAll() {
        seedUser("demo", "Дани", Gender.MALE, 28, 82.0, 185.0,
                HealthGoal.WEIGHT_LOSS, ActivityLevel.MODERATE);
        seedUser("maria", "Мария", Gender.FEMALE, 32, 58.0, 168.0,
                HealthGoal.MUSCLE_GAIN, ActivityLevel.ACTIVE);
        seedUser("ivan", "Иван", Gender.MALE, 52, 92.0, 175.0,
                HealthGoal.ENDURANCE, ActivityLevel.MODERATE);

        userRepo.findAll().forEach(u -> authService.ensurePassword(u.getUsername(), AuthService.DEMO_PASSWORD));
        log.info("Demo data initialization finished");
    }

    private void seedUser(String username, String displayName, Gender gender, int age,
                          double weight, double height, HealthGoal goal, ActivityLevel activity) {
        if (userRepo.findByUsername(username).isEmpty()) {
            authService.register(username, AuthService.DEMO_PASSWORD, displayName);
        }
        lifestyleService.createOrUpdateUser(UserProfile.builder()
                .username(username)
                .displayName(displayName)
                .gender(gender)
                .age(age)
                .weightKg(weight)
                .heightCm(height)
                .goal(goal)
                .activityLevel(activity)
                .build());
    }
}
