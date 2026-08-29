package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.config.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AuthHelper {

    /** The scope a FluxiBiz support account carries. */
    private static final String OPERATOR_AUTHORITY = "SCOPE_admin-business:manage";


    private AuthHelper() {
    }

    /**
     * Whether the caller is FluxiBiz staff acting on a shop's behalf.
     *
     * Support operators are not owners or staff of the shops they help, so the
     * ordinary tenant check would refuse them. This is the one authority that
     * lets them past it, and it is held only by platform accounts.
     */
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
