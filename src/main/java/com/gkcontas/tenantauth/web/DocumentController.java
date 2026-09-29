package com.gkcontas.tenantauth.web;

import com.gkcontas.tenantauth.dto.CreateDocumentRequest;
import com.gkcontas.tenantauth.dto.DocumentResponse;
import com.gkcontas.tenantauth.security.TenantPrincipal;
import com.gkcontas.tenantauth.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.listForCurrentTenant().stream()
                .map(DocumentResponse::summaryOf)
                .toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse read(@PathVariable Long id) {
        return DocumentResponse.from(documentService.read(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@AuthenticationPrincipal TenantPrincipal principal,
                                   @Valid @RequestBody CreateDocumentRequest request) {
        return DocumentResponse.from(documentService.create(principal, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable Long id) {
        documentService.delete(principal, id);
    }
}
