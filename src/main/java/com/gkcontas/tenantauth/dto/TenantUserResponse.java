package com.gkcontas.tenantauth.dto;

import com.gkcontas.tenantauth.model.User;

public record TenantUserResponse(Long id, String email, String department, int clearanceLevel, String role) {

    public static TenantUserResponse from(User user) {
        return new TenantUserResponse(user.getId(), user.getEmail(), user.getDepartment(),
                user.getClearanceLevel(), user.getRole().name());
    }
}
