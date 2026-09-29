package com.gkcontas.tenantauth.web;

import com.gkcontas.tenantauth.dto.CurrentUserResponse;
import com.gkcontas.tenantauth.dto.LoginRequest;
import com.gkcontas.tenantauth.dto.LoginResponse;
import com.gkcontas.tenantauth.security.JwtService;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import com.gkcontas.tenantauth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Date;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Didactic: shows exactly which claims the token is carrying. */
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal TenantPrincipal principal) {
        return CurrentUserResponse.from(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal TenantPrincipal principal, HttpServletRequest request) {
        // The expiry is read back from the token so the revocation entry lives exactly as
        // long as the token it invalidates, and not a second longer.
        String token = request.getHeader(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());
        Date expiration = jwtService.parse(token).getPayload().getExpiration();
        authService.logout(principal, expiration == null ? Instant.now() : expiration.toInstant());
    }
}
