package com.gkcontas.tenantauth.security;

import com.gkcontas.tenantauth.model.Document;
import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Backs {@code hasPermission(...)} in SpEL, so the expression in the annotation stays
 * short and the rule itself lives in code that can be unit tested.
 *
 * <p>The three conditions below are attribute-based authorization: the decision comes from
 * comparing attributes of the caller with attributes of the resource, not from a role. A
 * role-based model would need a role per (department, clearance) pair, which is how
 * systems end up with four hundred roles nobody can audit.
 */
@Component("documentPermissions")
public class DocumentPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        if (!(authentication instanceof TenantAuthentication tenantAuthentication)
                || !(target instanceof Document document)) {
            return false;
        }
        return isAllowed(tenantAuthentication.getPrincipal(), document, String.valueOf(permission));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        // Not supported on purpose. Resolving an id here would mean loading the document
        // a second time — once for the check and once for the method — and, worse, loading
        // it without the tenant filter, which is the query this whole project is about not
        // writing. The object-based form above is used instead, via @PostAuthorize.
        throw new UnsupportedOperationException(
                "Use hasPermission(returnObject, '...') so the document is fetched once, scoped by tenant");
    }

    /**
     * Public because the delete rule calls it directly from the service: a rule worth
     * writing once should be callable from both an annotation and ordinary code.
     */
    public boolean isAllowed(TenantPrincipal principal, Document document, String permission) {
        if (!document.getTenantId().equals(principal.tenantId())) {
            // Belt and braces. The query that produced this document was already scoped by
            // tenant; if it somehow was not, the answer is still no.
            return false;
        }
        boolean clearanceSufficient = principal.clearanceLevel() >= document.getRequiredClearance();
        boolean departmentMatches = document.getDepartment() == null
                || document.getDepartment().equals(principal.department())
                || principal.isAdmin();

        return switch (permission) {
            case "read" -> clearanceSufficient && departmentMatches;
            case "delete" -> principal.isAdmin() || document.getOwnerId().equals(principal.userId());
            default -> false;
        };
    }
}
