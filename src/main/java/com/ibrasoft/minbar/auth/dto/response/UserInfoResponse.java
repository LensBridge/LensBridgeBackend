package com.ibrasoft.minbar.auth.dto.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

import com.ibrasoft.minbar.auth.model.Role;

@Data
@AllArgsConstructor
@lombok.NoArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private boolean isVerified;
    private Set<Role> roles;
}
