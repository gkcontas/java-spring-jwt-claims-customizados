package com.gkcontas.tenantauth.repository;

import com.gkcontas.tenantauth.model.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);
}
