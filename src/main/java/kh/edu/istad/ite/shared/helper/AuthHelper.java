package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.config.security.SecurityUtils;

import java.util.UUID;

public final class AuthHelper {

    private AuthHelper() {
    }

    public static UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }
}
