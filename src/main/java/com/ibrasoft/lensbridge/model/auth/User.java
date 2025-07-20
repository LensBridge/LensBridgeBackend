package com.ibrasoft.lensbridge.model.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Document(collection = "users")
@Data
public class User {
  @Id
  private UUID id;

  @NotBlank
  private String firstName;

  @NotBlank
  private String lastName;

  @NotBlank
  @Indexed(unique = true)
  @Size(max = 10)
  private String studentNumber;

  @NotBlank
  @Size(max = 50)
  @Email
  @Indexed(unique = true)
  @JsonIgnore
  private String email;

  @NotBlank
  @Size(max = 120)
  private String password;

  private List<String> roles;

  private boolean verified;
  private String verificationToken;

  public User() {
    this.id = UUID.randomUUID();
  }

  public User(String firstName, String lastName, String studentNumber, String email, String password){
    this(
      UUID.randomUUID(),
      firstName,
      lastName,
      studentNumber,
      email,
      password,
      List.of(),
      false, 
      null
    );
  }

  public void addRole(String role) {
    if (this.roles == null) {
      this.roles = new ArrayList<>();
    }
    this.roles.add(role);
  }

  /**
   * Adds a role using the Role enum for type safety.
   * @param role the role to add
   */
  public void addRole(Role role) {
    addRole(role.getAuthority());
  }

  /**
   * Checks if the user has a specific role using the Role enum.
   * @param role the role to check for
   * @return true if the user has the role, false otherwise
   */
  public boolean hasRole(Role role) {
    return this.roles != null && this.roles.contains(role.getAuthority());
  }

  /**
   * Checks if the user has a specific role using a string.
   * @param role the role string to check for
   * @return true if the user has the role, false otherwise
   */
  public boolean hasRole(String role) {
    return this.roles != null && this.roles.contains(role);
  }

  /**
   * Removes a role using the Role enum.
   * @param role the role to remove
   */
  public void removeRole(Role role) {
    if (this.roles != null) {
      this.roles.remove(role.getAuthority());
    }
  }
}
