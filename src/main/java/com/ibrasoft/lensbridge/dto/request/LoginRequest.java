package com.ibrasoft.lensbridge.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    public void setEmail(String email) {
        this.email = (email == null) ? null : email.toLowerCase();
    }
}
