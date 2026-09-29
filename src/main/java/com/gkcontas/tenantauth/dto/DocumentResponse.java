package com.gkcontas.tenantauth.dto;

import com.gkcontas.tenantauth.model.Document;

public record DocumentResponse(
        Long id, String title, String content, Integer requiredClearance,
        String department, Long ownerId) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), document.getContent(),
                document.getRequiredClearance(), document.getDepartment(), document.getOwnerId());
    }

    /** Listing form: no content, so a listing never leaks a body the caller may not read. */
    public static DocumentResponse summaryOf(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), null,
                document.getRequiredClearance(), document.getDepartment(), document.getOwnerId());
    }
}
