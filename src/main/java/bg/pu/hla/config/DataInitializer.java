package bg.pu.hla.config;

import bg.pu.hla.domain.*;
import bg.pu.hla.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserProfileRepository userRepo;

    @Override
    public void run(String... args) {
        if (userRepo.findByUsername("demo").isEmpty()) {
            userRepo.save(UserProfile.builder()
                    .username("demo")
                    .displayName("Demo User")
                    .age(28)
                    .weightKg(75.0)
                    .heightCm(178.0)
                    .goal(HealthGoal.WEIGHT_LOSS)
                    .activityLevel(ActivityLevel.MODERATE)
                    .build());
        }
    }
}
