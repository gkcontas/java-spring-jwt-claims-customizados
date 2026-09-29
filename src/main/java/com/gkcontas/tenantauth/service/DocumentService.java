package com.gkcontas.tenantauth.service;

import com.gkcontas.tenantauth.dto.CreateDocumentRequest;
import com.gkcontas.tenantauth.exception.DocumentNotFoundException;
import com.gkcontas.tenantauth.model.Document;
import com.gkcontas.tenantauth.repository.DocumentRepository;
import com.gkcontas.tenantauth.security.DocumentPermissionEvaluator;
import com.gkcontas.tenantauth.security.TenantContext;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionEvaluator permissions;

    public DocumentService(DocumentRepository documentRepository, DocumentPermissionEvaluator permissions) {
        this.documentRepository = documentRepository;
        this.permissions = permissions;
    }

    /**
     * Two different refusals, and the difference is the point.
     *
     * <p>The query is scoped by tenant, so a document belonging to another tenant is simply
     * not found and the caller gets 404 — identical to asking for an id that never existed.
     * Only once the document is known to be inside the caller's own tenant does
     * {@code @PostAuthorize} get to say 403, which is safe because the caller already knows
     * it exists.
     *
     * <p>Answering 403 for the cross-tenant case would confirm the document exists, and
     * that confirmation is itself the leak: iterating ids and recording which ones answer
     * 403 instead of 404 maps out a competitor's data without reading a single document.
     */
    @PostAuthorize("hasPermission(returnObject, 'read')")
    @Transactional(readOnly = true)
    public Document read(Long id) {
        return documentRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** Listing is filtered by tenant, so it can never contain another tenant's rows. */
    @Transactional(readOnly = true)
    public List<Document> listForCurrentTenant() {
        return documentRepository.findAllByTenantIdOrderByIdAsc(TenantContext.require());
    }

    /**
     * A caller cannot create a document it would not be allowed to read. Without this, a
     * clearance-1 user could file something at clearance 5 and lock themselves out of it —
     * or, worse, use the system as a drop box for data they cannot see.
     */
    @PreAuthorize("principal.clearanceLevel() >= #request.requiredClearance()")
    @Transactional
    public Document create(TenantPrincipal principal, CreateDocumentRequest request) {
        Document document = new Document(
                principal.tenantId(), principal.userId(), request.title(), request.content(),
                request.requiredClearance(), request.department());
        return documentRepository.save(document);
    }

    /**
     * The delete rule is plain Java rather than a SpEL string.
     *
     * <p>It has to load the document first to know who owns it, and it has to distinguish
     * "not yours" from "not found" — two things an annotation cannot express without either
     * loading the row twice or collapsing the two answers into one.
     */
    @Transactional
    public void delete(TenantPrincipal principal, Long id) {
        Document document = documentRepository.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (!permissions.isAllowed(principal, document, "delete")) {
            throw new AccessDeniedException("Only the owner or an administrator may delete this document");
        }
        documentRepository.delete(document);
    }
}
