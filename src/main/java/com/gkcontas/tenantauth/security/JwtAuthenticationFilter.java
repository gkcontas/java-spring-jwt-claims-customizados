package com.gkcontas.tenantauth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionRevocationService revocationService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionRevocationService revocationService) {
        this.jwtService = jwtService;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            // Always, even when the request failed. A tenant left on a pooled thread is
            // handed to the next request that gets it.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = header.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parse(token).getPayload();
            TenantPrincipal principal = jwtService.toPrincipal(claims);

            if (revocationService.isRevoked(principal.sessionId())) {
                // Signature valid, expiry in the future, and still refused. A logged-out
                // session must not be resurrected by a token that has not expired yet.
                log.debug("Rejecting a token whose session {} was revoked", principal.sessionId());
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(new TenantAuthentication(principal));
            TenantContext.set(principal.tenantId());
        } catch (JwtException | IllegalArgumentException e) {
            // Left unauthenticated on purpose, and the reason never reaches the client:
            // telling a caller whether the signature or the expiry failed helps only
            // somebody probing the endpoint.
            log.debug("Rejecting a token that could not be verified: {}", e.getMessage());
        }
    }
}
