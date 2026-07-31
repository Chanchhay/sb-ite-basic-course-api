package kh.edu.istad.ite.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class AdminSecurityValidator {

    private final CurrentAuthorizationContext currentAuthorizationContext;

    public void validateSuperAdmin() {
        if (!currentAuthorizationContext.hasRealmRole("SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SUPER_ADMIN can perform this action");
        }
    }
}
