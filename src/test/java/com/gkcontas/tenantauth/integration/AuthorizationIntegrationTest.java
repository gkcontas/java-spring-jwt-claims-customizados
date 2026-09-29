package com.gkcontas.tenantauth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Authorization driven by the claims, not by roles.
 *
 * <p>Every decision below comes from comparing an attribute of the caller with an
 * attribute of the resource. A role-based model would need a role per (department,
 * clearance) pair, which is how systems end up with four hundred roles nobody can audit.
 */
class AuthorizationIntegrationTest extends IntegrationTestBase {

    @Test
    void shouldAllowReadingWithinClearanceAndDepartment() throws Exception {
        // ana: FINANCE, clearance 3. Document 1: FINANCE, requires 3.
        mockMvc.perform(get("/documents/{id}", 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "ana@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("ACME numbers"));
    }

    @Test
    void shouldForbidWhenClearanceIsTooLow() throws Exception {
        // Document 4 requires clearance 5; ana has 3. Same tenant, so 403 is safe here —
        // she already knows the document exists.
        mockMvc.perform(get("/documents/{id}", 4)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "ana@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void shouldForbidWhenTheDepartmentDiffers() throws Exception {
        // bruno: ENGINEERING, clearance 1. Document 1 is FINANCE.
        mockMvc.perform(get("/documents/{id}", 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "bruno@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReadingADocumentWithNoDepartmentRestriction() throws Exception {
        mockMvc.perform(get("/documents/{id}", 2)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "bruno@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("ACME handbook"));
    }

    @Test
    void shouldLetAnAdministratorCrossDepartmentsInsideTheTenant() throws Exception {
        String adminToken = tokenFor("acme", "admin@example.com");

        mockMvc.perform(get("/documents/{id}", 3).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        // ...and still not across tenants.
        mockMvc.perform(get("/documents/{id}", 5).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRefuseToCreateADocumentTheCallerCouldNotRead() throws Exception {
        // Without this a clearance-1 user could file something at clearance 5 and use the
        // system as a drop box for data they cannot see.
        mockMvc.perform(post("/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "bruno@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sneaky", "content": "x", "requiredClearance": 5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateADocumentInsideTheCallersTenantAndClearance() throws Exception {
        mockMvc.perform(post("/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "ana@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Budget draft", "content": "numbers",
                                 "requiredClearance": 2, "department": "FINANCE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Budget draft"));
    }

    @Test
    void shouldLetOnlyTheOwnerOrAnAdministratorDelete() throws Exception {
        String anaToken = tokenFor("acme", "ana@example.com");
        String brunoToken = tokenFor("acme", "bruno@example.com");

        String created = mockMvc.perform(post("/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(anaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "To delete", "content": "x", "requiredClearance": 1}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // Not the owner, not an admin — refused, even though the document is readable.
        mockMvc.perform(delete("/documents/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(brunoToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/documents/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(anaToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAnswerNotFoundWhenDeletingAcrossTenants() throws Exception {
        // Same rule as reading: the caller must not learn the document exists.
        mockMvc.perform(delete("/documents/{id}", 5)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "admin@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGuardTheAdminAreaWithBothRoleAndClearance() throws Exception {
        // The rule lives in AdminAccessAuthorizationManager: ROLE_ADMIN *and* clearance 5.
        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "ana@example.com"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "admin@example.com"))))
                .andExpect(status().isOk())
                // Three ACME users; the Globex one is not visible to an ACME admin.
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.email == 'ana@example.com')]").exists());
    }
}
