package com.bezkoder.springjwt.payload.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtResponse {
  private String token;
  private String firstName;
  private String lastName;
  private final String type = "Bearer";
  private UUID id;
  private String email;
  private List<String> roles;
}
