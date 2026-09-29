package com.gkcontas.tenantauth.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller, with the custom claims already typed.
 *
 * <p>Reading them back out of the token on every authorization check would mean parsing
 * and verifying the signature repeatedly, and would leave every rule dealing in
 * {@code Object} from a claims map. Doing it once in the filter and carrying a typed
 * object is what lets a rule read {@code principal.clearanceLevel() >= 3}.
 *
 * @param sessionId identifies this login, so it can be revoked without changing the
 *                  signing key or waiting for the token to expire
 */
public record TenantPrincipal(
        Long userId,
        String email,
        Long tenantId,
        String tenantSlug,
        String department,
        int clearanceLevel,
        String role,
        String sessionId) {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
