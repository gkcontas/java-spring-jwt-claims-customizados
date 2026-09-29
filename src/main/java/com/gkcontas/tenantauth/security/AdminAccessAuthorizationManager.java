package com.gkcontas.tenantauth.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * A rule that combines two attributes, expressed in code rather than in a SpEL string.
 *
 * <p>The equivalent expression would be
 * {@code hasRole('ADMIN') and principal.clearanceLevel() >= 5} — which works, and is the
 * moment to stop. A SpEL string is not type checked, not refactorable, not debuggable and
 * not unit testable; every condition added to it makes the rule harder to verify at the
 * exact point where being wrong is most expensive. An AuthorizationManager is ordinary
 * Java with a test next to it.
 */
@Component
public class AdminAccessAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    static final int REQUIRED_ADMIN_CLEARANCE = 5;

    /**
     * {@code check} is marked deprecated in favour of {@code authorize}, which returns the
     * richer {@code AuthorizationResult} — but it is still the abstract method on the
     * interface in this version, so implementing only {@code authorize} does not compile.
     * Implementing it here is the correct choice today and the line to revisit when the
     * interface finally drops it.
     */
    @SuppressWarnings("deprecation")
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (!(current instanceof TenantAuthentication tenantAuthentication)) {
            return new AuthorizationDecision(false);
        }
        TenantPrincipal principal = tenantAuthentication.getPrincipal();
        boolean allowed = principal.isAdmin() && principal.clearanceLevel() >= REQUIRED_ADMIN_CLEARANCE;
        return new AuthorizationDecision(allowed);
    }
}
