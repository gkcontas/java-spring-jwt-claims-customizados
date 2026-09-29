package com.gkcontas.tenantauth.security;

import com.gkcontas.tenantauth.config.JwtProperties;
import com.gkcontas.tenantauth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String TENANT_ID_CLAIM = "tenantId";
    public static final String TENANT_SLUG_CLAIM = "tenantSlug";
    public static final String DEPARTMENT_CLAIM = "department";
    public static final String CLEARANCE_CLAIM = "clearance";
    public static final String ROLE_CLAIM = "role";
    public static final String SESSION_ID_CLAIM = "sid";
    public static final String USER_ID_CLAIM = "uid";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Note what is <em>not</em> in here: no name, no password hash, no document contents.
     *
     * <p>A JWT is signed, not encrypted. Anyone holding it can Base64-decode the payload
     * and read every claim — the signature proves the claims were not altered, and proves
     * nothing about who can see them. Every value below is deliberately non-sensitive.
     */
    public IssuedToken issue(User user) {
        String sessionId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .claims(Map.of(
                        USER_ID_CLAIM, user.getId(),
                        TENANT_ID_CLAIM, user.getTenant().getId(),
                        TENANT_SLUG_CLAIM, user.getTenant().getSlug(),
                        DEPARTMENT_CLAIM, user.getDepartment(),
                        CLEARANCE_CLAIM, user.getClearanceLevel(),
                        ROLE_CLAIM, user.getRole().name(),
                        SESSION_ID_CLAIM, sessionId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();

        return new IssuedToken(token, sessionId, expiresAt);
    }

    /**
     * Parsing pins the algorithm and the issuer.
     *
     * <p>Pinning matters: the historical "alg: none" attack works by rewriting the header
     * so the verifier is told no signature is needed, and algorithm-confusion attacks swap
     * an RSA token for an HMAC one signed with the public key. {@code verifyWith} on a
     * SecretKey restricts verification to HMAC, and jjwt rejects "none" outright.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token);
    }

    public TenantPrincipal toPrincipal(Claims claims) {
        return new TenantPrincipal(
                claims.get(USER_ID_CLAIM, Number.class).longValue(),
                claims.getSubject(),
                claims.get(TENANT_ID_CLAIM, Number.class).longValue(),
                claims.get(TENANT_SLUG_CLAIM, String.class),
                claims.get(DEPARTMENT_CLAIM, String.class),
                claims.get(CLEARANCE_CLAIM, Number.class).intValue(),
                claims.get(ROLE_CLAIM, String.class),
                claims.get(SESSION_ID_CLAIM, String.class));
    }

    public record IssuedToken(String token, String sessionId, Instant expiresAt) {
    }
}
