package com.gkcontas.tenantauth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param tenantSlug required, because the email alone does not identify a user. Two
 *                   tenants may both have ana@example.com, and the seed data has exactly
 *                   that so the tests cannot accidentally pass.
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank") String tenantSlug,
        @NotBlank(message = "must not be blank") String email,
        @NotBlank(message = "must not be blank") String password) {
}
