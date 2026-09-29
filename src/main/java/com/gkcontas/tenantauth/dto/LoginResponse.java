package com.gkcontas.tenantauth.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt) {
}
