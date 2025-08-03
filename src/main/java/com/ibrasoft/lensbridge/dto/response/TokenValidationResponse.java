package com.ibrasoft.lensbridge.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenValidationResponse {
    private boolean valid;
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private boolean verified;
    private List<String> roles;
}
