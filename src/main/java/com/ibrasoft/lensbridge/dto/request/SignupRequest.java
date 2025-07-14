package com.bezkoder.springjwt.payload.request;

import lombok.Data;

import jakarta.validation.constraints.*;

@Data
public class SignupRequest {
  @NotBlank
  private String firstName;

  @NotBlank
  private String lastName;

  @NotBlank
  @Size(max = 10)
  private String studentNumber;

  @NotBlank
  @Size(max = 50)
  @Email
  @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]*\\.utoronto\\.ca$", 
           message = "Email must be a valid University of Toronto email address (*.utoronto.ca)")
  private String email;

  @NotBlank
  @Size(min = 6, max = 40)
  private String password;
}
