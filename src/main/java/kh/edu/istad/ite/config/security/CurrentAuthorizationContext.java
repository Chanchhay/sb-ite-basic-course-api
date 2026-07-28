package kh.edu.istad.ite.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class CurrentAuthorizationContext {

    private Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    public Set<String> getRealmRoles() {
        Jwt jwt = getJwt();
        if (jwt == null) return Collections.emptySet();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            Set<String> roleSet = new HashSet<>();
            for (Object role : roles) {
                if (role instanceof String s) {
                    roleSet.add(s);
                }
            }
            return roleSet;
        }
        return Collections.emptySet();
    }

    public Set<String> getPermissions() {
        Jwt jwt = getJwt();
        if (jwt == null) return Collections.emptySet();

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && resourceAccess.get("fluxipos-backend") instanceof Map<?, ?> backendAccess) {
            if (backendAccess.get("roles") instanceof Collection<?> permissions) {
                Set<String> permissionSet = new HashSet<>();
                for (Object perm : permissions) {
                    if (perm instanceof String s) {
                        permissionSet.add(s);
                    }
                }
                return permissionSet;
            }
        }
        return Collections.emptySet();
    }

    public boolean hasRealmRole(String role) {
        return getRealmRoles().contains(role);
    }

    public boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }

    public String getCurrentRole() {
        Set<String> realmRoles = getRealmRoles();
        
        // 1. biz_...
        for (String role : realmRoles) {
            if (role.startsWith("biz_")) {
                return role;
            }
        }
        
        // 2. platform_...
        for (String role : realmRoles) {
            if (role.startsWith("platform_")) {
                return role;
            }
        }
        
        // 3. SUPER_ADMIN
        if (realmRoles.contains("SUPER_ADMIN")) {
            return "SUPER_ADMIN";
        }
        
        // 4. BUSINESS
        if (realmRoles.contains("BUSINESS")) {
            return "BUSINESS";
        }
        
        // 5. GLOBAL_CUSTOMER
        if (realmRoles.contains("GLOBAL_CUSTOMER")) {
            return "GLOBAL_CUSTOMER";
        }
        
        // 6. USER
        if (realmRoles.contains("USER")) {
            return "USER";
        }
        
        return null;
    }
}
