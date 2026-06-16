package bg.pu.hla.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String currentUsername(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return auth.getName();
    }

    public static void ensureSameUser(Authentication auth, String username) {
        if (auth == null || !auth.isAuthenticated() || !auth.getName().equals(username)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
