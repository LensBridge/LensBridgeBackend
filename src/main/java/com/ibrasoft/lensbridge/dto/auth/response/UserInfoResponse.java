package com.ibrasoft.lensbridge.dto.auth.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

import com.ibrasoft.lensbridge.model.auth.Role;

@Data
@AllArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private boolean isVerified;
    private Set<Role> roles;
}
