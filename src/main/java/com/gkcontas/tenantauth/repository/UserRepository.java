package com.gkcontas.tenantauth.repository;

import com.gkcontas.tenantauth.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Scoped by tenant, because the email alone is not unique. A lookup by email only
     * would authenticate against whichever tenant's row came back first.
     */
    Optional<User> findByTenantSlugAndEmail(String tenantSlug, String email);
}
