package com.gkcontas.tenantauth.service;

import com.gkcontas.tenantauth.dto.LoginRequest;
import com.gkcontas.tenantauth.dto.LoginResponse;
import com.gkcontas.tenantauth.exception.InvalidCredentialsException;
import com.gkcontas.tenantauth.model.User;
import com.gkcontas.tenantauth.repository.UserRepository;
import com.gkcontas.tenantauth.security.JwtService;
import com.gkcontas.tenantauth.security.SessionRevocationService;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionRevocationService revocationService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SessionRevocationService revocationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revocationService = revocationService;
    }

    /**
     * The one place where the custom claims are decided.
     *
     * <p>They are read from the database <em>now</em> and frozen into the token. From this
     * moment the token says what was true at login, and keeps saying it until it expires —
     * which is the trade the whole design rests on, and the reason the TTL is fifteen
     * minutes rather than a day.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository
                .findByTenantSlugAndEmail(request.tenantSlug(), request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !user.getTenant().isActive()) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt());
    }

    /**
     * Logout on a stateless scheme: the token cannot be taken back, so the session it
     * carries is added to the revocation list until the token would have expired anyway.
     */
    public void logout(TenantPrincipal principal, Instant tokenExpiresAt) {
        revocationService.revoke(principal.sessionId(), tokenExpiresAt);
    }
}
