package com.ibrasoft.lensbridge.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    
    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message = 
        "First name can only contain letters, spaces, hyphens, and apostrophes")
    private String firstName;

    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message = 
        "Last name can only contain letters, spaces, hyphens, and apostrophes")
    private String lastName;
    
    @Size(max = 10)
    private String studentNumber;
}
