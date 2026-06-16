package bg.pu.hla.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import bg.pu.hla.domain.UserProfile;
import bg.pu.hla.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserProfileRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserProfile profile = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (profile.getPasswordHash() == null || profile.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("User has no password set: " + username);
        }

        return User.builder()
                .username(profile.getUsername())
                .password(profile.getPasswordHash())
                .roles("USER")
                .build();
    }
}
