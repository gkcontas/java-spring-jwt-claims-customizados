package com.gkcontas.tenantauth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.tenantauth.config.JwtProperties;
import com.gkcontas.tenantauth.model.Tenant;
import com.gkcontas.tenantauth.model.User;
import com.gkcontas.tenantauth.model.UserRole;
import com.gkcontas.tenantauth.security.JwtService;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "a-test-secret-that-is-comfortably-long-enough-for-hs512-signing-0123456789";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofMinutes(15), "test-issuer"));

    private User sampleUser() {
        Tenant tenant = new Tenant("acme", "ACME Industries");
        tenant.setId(7L);
        User user = new User(tenant, "ana@example.com", "irrelevant-hash", "FINANCE", 3, UserRole.ROLE_USER);
        user.setId(42L);
        return user;
    }

    @Test
    void shouldCarryEveryCustomClaim() {
        JwtService.IssuedToken issued = jwtService.issue(sampleUser());
        Claims claims = jwtService.parse(issued.token()).getPayload();

        assertThat(claims.getSubject()).isEqualTo("ana@example.com");
        assertThat(claims.get(JwtService.TENANT_ID_CLAIM, Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.get(JwtService.TENANT_SLUG_CLAIM, String.class)).isEqualTo("acme");
        assertThat(claims.get(JwtService.DEPARTMENT_CLAIM, String.class)).isEqualTo("FINANCE");
        assertThat(claims.get(JwtService.CLEARANCE_CLAIM, Number.class).intValue()).isEqualTo(3);
        assertThat(claims.get(JwtService.SESSION_ID_CLAIM, String.class)).isEqualTo(issued.sessionId());
    }

    @Test
    void shouldProduceATypedPrincipalSoRulesDoNotDealInObjects() {
        JwtService.IssuedToken issued = jwtService.issue(sampleUser());

        TenantPrincipal principal = jwtService.toPrincipal(jwtService.parse(issued.token()).getPayload());

        assertThat(principal.tenantId()).isEqualTo(7L);
        assertThat(principal.clearanceLevel()).isEqualTo(3);
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    void shouldLeaveThePayloadReadableToAnyoneHoldingTheToken() {
        String token = jwtService.issue(sampleUser()).token();

        String payload = new String(Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        // Not a defect — a demonstration. A JWT is signed, not encrypted: the signature
        // proves the claims were not altered and proves nothing about who can read them.
        // This is exactly why no name, no document content and no personal identifier goes
        // into a claim.
        assertThat(payload).contains("\"department\":\"FINANCE\"").contains("\"clearance\":3");
    }

    @Test
    void shouldRejectATokenSignedWithAnotherKey() {
        JwtService other = new JwtService(new JwtProperties(
                "a-completely-different-secret-also-long-enough-for-hs512-signing-abcdef",
                Duration.ofMinutes(15), "test-issuer"));

        assertThatThrownBy(() -> jwtService.parse(other.issue(sampleUser()).token()))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectATokenFromAnotherIssuer() {
        JwtService other = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15), "someone-else"));

        // Same key, different issuer. Pinning the issuer stops a token minted for a
        // sibling service being replayed against this one.
        assertThatThrownBy(() -> jwtService.parse(other.issue(sampleUser()).token()))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void shouldRejectAnExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(
                new JwtProperties(SECRET, Duration.ofMillis(1), "test-issuer"));
        String token = shortLived.issue(sampleUser()).token();

        Thread.sleep(20);

        assertThatThrownBy(() -> shortLived.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectAnUnsignedTokenBuiltByHand() {
        // The historical "alg: none" attack: rewrite the header so the verifier is told no
        // signature is needed. Pinning verification to an HMAC key makes it impossible.
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"attacker@example.com\",\"clearance\":5}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> jwtService.parse(header + "." + payload + "."))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
