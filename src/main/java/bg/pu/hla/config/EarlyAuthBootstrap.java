package bg.pu.hla.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import bg.pu.hla.repository.UserProfileRepository;
import bg.pu.hla.service.AuthService;

@Slf4j
@Component
@RequiredArgsConstructor
public class EarlyAuthBootstrap {

    private final UserProfileRepository userRepo;
    private final AuthService authService;

    @PostConstruct
    void preparePasswordsBeforeWebServer() {
        userRepo.findAll().forEach(user ->
                authService.ensurePassword(user.getUsername(), AuthService.DEMO_PASSWORD));
        log.debug("Prepared login passwords for {} user(s)", userRepo.count());
    }
}
