package com.gkcontas.tenantauth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * The answer to "you cannot log out of a JWT".
 *
 * <p>You cannot invalidate the token — it is self-contained and stays cryptographically
 * valid until it expires. What you can do is stop honouring the session it names.
 */
class SessionRevocationIntegrationTest extends IntegrationTestBase {

    @Test
    void shouldStopHonouringATokenAfterLogout() throws Exception {
        String token = tokenFor("acme", "ana@example.com");

        mockMvc.perform(get("/documents").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // The very same token: signature still valid, expiry still in the future, and
        // refused. Without the revocation list it would keep working for the rest of its
        // fifteen minutes, which is what "logout does nothing on a JWT" means in practice.
        mockMvc.perform(get("/documents").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLeaveOtherSessionsOfTheSameUserAlone() throws Exception {
        String firstSession = tokenFor("acme", "ana@example.com");
        String secondSession = tokenFor("acme", "ana@example.com");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(firstSession)))
                .andExpect(status().isNoContent());

        // Revocation is per session id, not per user: logging out on a laptop must not
        // sign the same person out on their phone.
        mockMvc.perform(get("/documents").header(HttpHeaders.AUTHORIZATION, bearer(secondSession)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGiveEachLoginItsOwnSessionId() throws Exception {
        String first = tokenFor("acme", "ana@example.com");
        String second = tokenFor("acme", "ana@example.com");

        String firstSessionId = sessionIdOf(first);
        String secondSessionId = sessionIdOf(second);

        org.assertj.core.api.Assertions.assertThat(firstSessionId).isNotEqualTo(secondSessionId);
    }

    private String sessionIdOf(String token) throws Exception {
        String body = mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asText();
    }
}
