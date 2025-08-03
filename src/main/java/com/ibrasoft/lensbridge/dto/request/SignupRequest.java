package com.ibrasoft.lensbridge.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class SignupRequest {
  @NotBlank
  @Size(max = 20)
  @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message =
  "First name can only contain letters, spaces, hyphens, and apostrophes")
  private String firstName;

  @NotBlank
  @Size(max = 20)
  @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message = "Last name can only contain letters, spaces, hyphens, and apostrophes")
  private String lastName;

  @NotBlank
  @Size(max = 10)
  private String studentNumber;

  @NotBlank
  @Size(max = 50)
  @Email
  @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]*\\.utoronto\\.ca$", message = "Email must be a valid University of Toronto email address (*.utoronto.ca)")
  private String email;

  @NotBlank
  @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters long")
  private String password;

  public void setEmail(String email){
    this.email = email.toLowerCase();
  }
  
}
