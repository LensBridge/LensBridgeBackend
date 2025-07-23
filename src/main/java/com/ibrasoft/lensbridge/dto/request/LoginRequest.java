package com.ibrasoft.lensbridge.dto.request;

import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        this.email = this.email == null? null: this.email.toLowerCase();
    }
}
