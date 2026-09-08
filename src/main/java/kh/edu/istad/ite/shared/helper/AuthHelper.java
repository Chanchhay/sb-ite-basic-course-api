package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.config.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AuthHelper {

    private static final String OPERATOR_AUTHORITY = "SCOPE_admin-business:manage";


    private AuthHelper() {
    }


    public static boolean isPlatformOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OPERATOR_AUTHORITY::equals);
    }

    public static UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }
}
