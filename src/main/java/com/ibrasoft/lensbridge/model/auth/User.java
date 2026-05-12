package com.ibrasoft.lensbridge.model.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Id;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email")
})
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  private String firstName;

  @NotBlank
  private String lastName;

  @NotBlank
  @Size(max = 10)
  @Column(unique = true, nullable = false)
  private String studentNumber;

  @NotBlank
  @Size(max = 254)
  @Email
  @Column(nullable = false, unique = true)
  private String email;

  @NotBlank
  @JsonIgnore
  @Column(nullable = false)
  private String passwordHash;

  @ElementCollection(targetClass = Role.class)
  @Enumerated(EnumType.STRING)
  @CollectionTable(name = "user_roles")
  @Column(name = "role", nullable = false)
  private Set<Role> roles = new HashSet<>();

  private Instant verifiedAt;

  public User(String firstName, String lastName, String studentNumber, String email, String passwordHash) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.studentNumber = studentNumber;
    this.email = email;
    this.passwordHash = passwordHash;
    this.roles = new HashSet<>();
  }

  public String getPassword() {
    return passwordHash;
  }

  public void setPassword(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void addRole(Role role) {
    if (roles == null) roles = new HashSet<>();
    roles.add(role);
  }

  public boolean hasRole(Role role) {
    return roles != null && roles.contains(role);
  }

  public boolean isVerified() {
    return verifiedAt != null;
  }
}
