package com.gkcontas.tenantauth.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.tenantauth.model.Document;
import com.gkcontas.tenantauth.security.DocumentPermissionEvaluator;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import org.junit.jupiter.api.Test;

/**
 * The authorization matrix, unit tested — which is the argument for putting the rule in a
 * class instead of in a SpEL string. None of this would be reachable from a test if it
 * lived inside an annotation.
 */
class DocumentPermissionEvaluatorTest {

    private final DocumentPermissionEvaluator evaluator = new DocumentPermissionEvaluator();

    private TenantPrincipal principal(long tenantId, String department, int clearance, String role) {
        return new TenantPrincipal(1L, "ana@example.com", tenantId, "acme",
                department, clearance, role, "session-1");
    }

    private Document document(long tenantId, long ownerId, int requiredClearance, String department) {
        Document document = new Document(tenantId, ownerId, "title", "content", requiredClearance, department);
        document.setId(10L);
        return document;
    }

    @Test
    void shouldAllowReadWhenClearanceAndDepartmentMatch() {
        assertThat(evaluator.isAllowed(
                principal(1L, "FINANCE", 3, "ROLE_USER"),
                document(1L, 1L, 3, "FINANCE"), "read")).isTrue();
    }

    @Test
    void shouldRefuseReadWhenClearanceIsTooLow() {
        assertThat(evaluator.isAllowed(
                principal(1L, "FINANCE", 2, "ROLE_USER"),
                document(1L, 1L, 3, "FINANCE"), "read")).isFalse();
    }

    @Test
    void shouldRefuseReadWhenTheDepartmentDiffers() {
        assertThat(evaluator.isAllowed(
                principal(1L, "ENGINEERING", 5, "ROLE_USER"),
                document(1L, 1L, 1, "FINANCE"), "read")).isFalse();
    }

    @Test
    void shouldAllowReadOfADocumentWithNoDepartmentRestriction() {
        assertThat(evaluator.isAllowed(
                principal(1L, "ENGINEERING", 1, "ROLE_USER"),
                document(1L, 1L, 1, null), "read")).isTrue();
    }

    @Test
    void shouldLetAnAdministratorCrossDepartmentsButNotTenants() {
        TenantPrincipal admin = principal(1L, "FINANCE", 5, "ROLE_ADMIN");

        assertThat(evaluator.isAllowed(admin, document(1L, 9L, 5, "ENGINEERING"), "read")).isTrue();
        // Admin is a role inside a tenant, not above it.
        assertThat(evaluator.isAllowed(admin, document(2L, 9L, 1, null), "read")).isFalse();
    }

    @Test
    void shouldRefuseAnyPermissionAcrossTenants() {
        TenantPrincipal principal = principal(1L, "FINANCE", 5, "ROLE_USER");

        assertThat(evaluator.isAllowed(principal, document(2L, 1L, 1, "FINANCE"), "read")).isFalse();
        assertThat(evaluator.isAllowed(principal, document(2L, 1L, 1, "FINANCE"), "delete")).isFalse();
    }

    @Test
    void shouldAllowDeleteForTheOwnerOrAnAdministrator() {
        Document document = document(1L, 1L, 1, "FINANCE");

        assertThat(evaluator.isAllowed(principal(1L, "FINANCE", 1, "ROLE_USER"), document, "delete")).isTrue();
        assertThat(evaluator.isAllowed(
                new TenantPrincipal(99L, "other@example.com", 1L, "acme", "FINANCE", 5, "ROLE_USER", "s"),
                document, "delete")).isFalse();
        assertThat(evaluator.isAllowed(
                new TenantPrincipal(99L, "admin@example.com", 1L, "acme", "FINANCE", 5, "ROLE_ADMIN", "s"),
                document, "delete")).isTrue();
    }

    @Test
    void shouldRefuseAnUnknownPermission() {
        assertThat(evaluator.isAllowed(
                principal(1L, "FINANCE", 5, "ROLE_ADMIN"),
                document(1L, 1L, 1, null), "publish")).isFalse();
    }
}
