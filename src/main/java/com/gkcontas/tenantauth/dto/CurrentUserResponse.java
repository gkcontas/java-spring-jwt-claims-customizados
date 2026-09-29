package com.gkcontas.tenantauth.dto;

import com.gkcontas.tenantauth.security.TenantPrincipal;

/** The decoded claims, exposed so the effect of each one can be seen from a browser. */
public record CurrentUserResponse(
        Long userId, String email, Long tenantId, String tenantSlug,
        String department, int clearanceLevel, String role, String sessionId) {

    public static CurrentUserResponse from(TenantPrincipal principal) {
        return new CurrentUserResponse(principal.userId(), principal.email(), principal.tenantId(),
                principal.tenantSlug(), principal.department(), principal.clearanceLevel(),
                principal.role(), principal.sessionId());
    }
}
