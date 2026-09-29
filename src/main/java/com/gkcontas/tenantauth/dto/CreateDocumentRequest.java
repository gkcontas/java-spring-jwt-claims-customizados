package com.gkcontas.tenantauth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentRequest(
        @NotBlank(message = "must not be blank") String title,
        @NotBlank(message = "must not be blank") String content,
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 5, message = "must be at most 5") Integer requiredClearance,
        String department) {
}
