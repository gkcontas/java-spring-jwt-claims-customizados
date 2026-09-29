package com.gkcontas.tenantauth.repository;

import com.gkcontas.tenantauth.model.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder takes a tenant id, and none of them is named {@code findById}.
 *
 * <p>That omission is deliberate and is the most important design decision in the
 * project. {@code JpaRepository.findById} is inherited and cannot be removed, so the
 * service layer must never call it — but relying on that being remembered is exactly the
 * failure mode this project is about. Naming every safe method with its tenant parameter
 * makes the unsafe call visibly different at the call site.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndTenantId(Long id, Long tenantId);

    List<Document> findAllByTenantIdOrderByIdAsc(Long tenantId);

    long countByTenantId(Long tenantId);
}
