package com.gkcontas.tenantauth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        // One message for every failure: unknown tenant, unknown email, wrong password,
        // deactivated account. Distinguishing them turns the login endpoint into an
        // oracle for which accounts exist.
        super("Invalid tenant, email or password");
    }
}
