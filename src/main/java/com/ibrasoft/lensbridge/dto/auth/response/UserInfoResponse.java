package com.ibrasoft.lensbridge.dto.auth.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import com.ibrasoft.lensbridge.model.auth.Role;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private boolean isVerified;
    private Set<Role> roles;
    private List<String> directPermissions;
    private List<String> effectivePermissions;

    public UserInfoResponse(UUID id, String firstName, String lastName, String email,
                            String studentNumber, boolean isVerified, Set<Role> roles) {
        this(id, firstName, lastName, email, studentNumber, isVerified, roles, List.of(), List.of());
    }
}
