package com.gkcontas.tenantauth.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Carries the {@link TenantPrincipal} through the SecurityContext.
 *
 * <p>Always authenticated: it is only ever constructed after the token has been verified,
 * so there is no unauthenticated state to represent.
 */
public class TenantAuthentication extends AbstractAuthenticationToken {

    private final TenantPrincipal principal;

    public TenantAuthentication(TenantPrincipal principal) {
        super(principal.authorities());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public TenantPrincipal getPrincipal() {
        return principal;
    }

    /**
     * Returns null on purpose. The credential was the token, it has already been verified,
     * and keeping it here would put a valid bearer token into every log line that prints
     * the authentication object.
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return super.getAuthorities();
    }
}
