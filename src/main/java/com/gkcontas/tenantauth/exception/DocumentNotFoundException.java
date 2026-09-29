package com.gkcontas.tenantauth.exception;

/**
 * Thrown for a document that does not exist <em>and</em> for one that belongs to another
 * tenant. The two cases are deliberately indistinguishable — see the README on why this
 * is a 404 and not a 403.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long id) {
        super("No document with id %d".formatted(id));
    }
}
