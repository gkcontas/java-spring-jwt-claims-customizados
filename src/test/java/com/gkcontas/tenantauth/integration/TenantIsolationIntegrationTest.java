package com.gkcontas.tenantauth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * The isolation tests, and the one that matters most is the 404.
 *
 * <p>The seed puts a document titled "Quarterly results" in both tenants, and the same
 * email address in both. Isolation tested only against data that could not collide anyway
 * has not been tested.
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Test
    void shouldAnswerNotFoundForAnotherTenantsDocument() throws Exception {
        String acmeToken = tokenFor("acme", "ana@example.com");

        // Document 5 belongs to Globex and exists. ACME's user gets 404 — byte for byte
        // the same answer as asking for an id that never existed.
        //
        // Answering 403 would confirm the document is there, and that confirmation is the
        // leak: walking the id range and recording which ones answer 403 instead of 404
        // maps out a competitor's data without reading a single document.
        mockMvc.perform(get("/documents/{id}", 5).header(HttpHeaders.AUTHORIZATION, bearer(acmeToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document not found"));

        mockMvc.perform(get("/documents/{id}", 99_999).header(HttpHeaders.AUTHORIZATION, bearer(acmeToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document not found"));
    }

    @Test
    void shouldListOnlyTheCallersOwnTenant() throws Exception {
        String acmeToken = tokenFor("acme", "ana@example.com");
        String globexToken = tokenFor("globex", "ana@example.com");

        // Asserted by membership rather than by count: other tests in the suite create
        // documents in ACME, so an absolute count would make this pass or fail depending
        // on execution order. The property under test is "nothing from the other tenant",
        // and that is what is checked.
        mockMvc.perform(get("/documents").header(HttpHeaders.AUTHORIZATION, bearer(acmeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 1)]").exists())
                .andExpect(jsonPath("$[?(@.id == 4)]").exists())
                .andExpect(jsonPath("$[?(@.id == 5)]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == 6)]").doesNotExist());

        mockMvc.perform(get("/documents").header(HttpHeaders.AUTHORIZATION, bearer(globexToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 5)]").exists())
                .andExpect(jsonPath("$[?(@.id == 6)]").exists())
                .andExpect(jsonPath("$[?(@.id == 1)]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == 4)]").doesNotExist());
    }

    @Test
    void shouldAuthenticateTheRightUserWhenTwoTenantsShareAnEmail() throws Exception {
        // Same address in both tenants, different clearance. A lookup by email alone would
        // authenticate against whichever row came back first.
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("acme", "ana@example.com"))))
                .andExpect(jsonPath("$.tenantSlug").value("acme"))
                .andExpect(jsonPath("$.clearanceLevel").value(3));

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("globex", "ana@example.com"))))
                .andExpect(jsonPath("$.tenantSlug").value("globex"))
                .andExpect(jsonPath("$.clearanceLevel").value(4));
    }

    @Test
    void shouldRefuseARequestWithNoToken() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid access token is required."));
    }

    @Test
    void shouldRefuseLoginWithoutRevealingWhichPartWasWrong() throws Exception {
        // Unknown tenant, unknown email and wrong password all answer identically.
        for (String body : new String[]{
                "{\"tenantSlug\":\"nosuch\",\"email\":\"ana@example.com\",\"password\":\"secret123\"}",
                "{\"tenantSlug\":\"acme\",\"email\":\"nobody@example.com\",\"password\":\"secret123\"}",
                "{\"tenantSlug\":\"acme\",\"email\":\"ana@example.com\",\"password\":\"wrong\"}"}) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/auth/login")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid tenant, email or password"));
        }
    }
}
