package com.ibrasoft.lensbridge.dto.auth.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import com.ibrasoft.lensbridge.model.auth.Role;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfoResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private boolean isVerified;
    private Audience audience;
    private Set<Role> roles;
    private List<String> directPermissions;
    private List<String> effectivePermissions;

    public UserInfoResponse(UUID id, String firstName, String lastName, String email,
                            boolean isVerified, Audience audience, Set<Role> roles) {
        this(id, firstName, lastName, email, isVerified, audience, roles, List.of(), List.of());
    }

    public static UserInfoResponse of(User user, List<String> effectivePermissions, List<String> directPermissions) {
        return builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .isVerified(user.isVerified())
                .audience(user.getAudience())
                .roles(user.getRoles())
                .effectivePermissions(effectivePermissions)
                .directPermissions(directPermissions)
                .build();
    }

    public static UserInfoResponse of(User user) {
        return of(user, List.of(), List.of());
    }
}
