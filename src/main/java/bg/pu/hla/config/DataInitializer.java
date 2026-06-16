package bg.pu.hla.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import bg.pu.hla.domain.*;
import bg.pu.hla.repository.UserProfileRepository;
import bg.pu.hla.service.LifestyleService;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserProfileRepository userRepo;
    private final LifestyleService lifestyleService;

    @Override
    public void run(String... args) {
        seedUser("demo", "Demo User", 28, 75.0, 178.0,
                HealthGoal.WEIGHT_LOSS, ActivityLevel.MODERATE);
        seedUser("maria", "Мария", 32, 62.0, 168.0,
                HealthGoal.MUSCLE_GAIN, ActivityLevel.ACTIVE);
        seedUser("ivan", "Иван", 45, 88.0, 175.0,
                HealthGoal.ENDURANCE, ActivityLevel.MODERATE);
    }

    private void seedUser(String username, String displayName, int age,
                          double weight, double height, HealthGoal goal, ActivityLevel activity) {
        if (userRepo.findByUsername(username).isEmpty()) {
            lifestyleService.createOrUpdateUser(UserProfile.builder()
                    .username(username)
                    .displayName(displayName)
                    .age(age)
                    .weightKg(weight)
                    .heightCm(height)
                    .goal(goal)
                    .activityLevel(activity)
                    .build());
        }
    }
}
