package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.repository.UserProfileRepository;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String DEMO_PASSWORD = "demo123";
    private static final Set<String> DEMO_USERS = Set.of("demo", "maria", "ivan");

    private final UserProfileRepository userRepo;
    private final OntologyService ontologyService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserProfile register(String username, String rawPassword, String displayName) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepo.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }

        UserProfile profile = UserProfile.builder()
                .username(username.trim())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName != null && !displayName.isBlank() ? displayName.trim() : username.trim())
                .goal(HealthGoal.MAINTENANCE)
                .activityLevel(ActivityLevel.MODERATE)
                .gender(Gender.UNSPECIFIED)
                .build();

        UserProfile saved = userRepo.save(profile);
        ontologyService.syncPersonProfile(saved);
        return saved;
    }

    @Transactional
    public void ensurePassword(String username, String rawPassword) {
        userRepo.findByUsername(username).ifPresent(user -> {
            boolean demoAccount = DEMO_USERS.contains(user.getUsername());
            if (demoAccount || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                userRepo.save(user);
            }
        });
    }
}
