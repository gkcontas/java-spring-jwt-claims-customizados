package com.gkcontas.tenantauth.web;

import com.gkcontas.tenantauth.dto.TenantUserResponse;
import com.gkcontas.tenantauth.repository.UserRepository;
import com.gkcontas.tenantauth.security.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reached only through {@code AdminAccessAuthorizationManager}, which requires the admin
 * role <em>and</em> clearance 5. Note there is no annotation here saying so: the rule is
 * applied at the URL level in the filter chain.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public List<TenantUserResponse> users() {
        // Even an administrator sees only their own tenant. Admin is a role inside a
        // tenant, not above it — a distinction multi-tenant systems get wrong regularly.
        Long tenantId = TenantContext.require();
        return userRepository.findAll().stream()
                .filter(user -> user.getTenant().getId().equals(tenantId))
                .map(TenantUserResponse::from)
                .toList();
    }
}
