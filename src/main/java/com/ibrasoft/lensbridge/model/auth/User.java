package com.ibrasoft.lensbridge.model.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ibrasoft.lensbridge.model.minbar.Audience;
import jakarta.persistence.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
  @Size(max = 254)
  @Email
  @Column(nullable = false, unique = true)
  private String email;

  @NotBlank
  @JsonIgnore
  @Column(nullable = false)
  private String passwordHash;

  @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  @CollectionTable(name = "user_roles")
  @Column(name = "role", nullable = false)
  private Set<Role> roles = new HashSet<>();
  
  @ElementCollection(targetClass = Permission.class, fetch = FetchType.LAZY)
  @Enumerated(EnumType.STRING)
  @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "permission", nullable = false)
  private Set<Permission> directPermissions = new HashSet<>();

  @Column()
  private Instant verifiedAt;

  @Column()
  @Enumerated(EnumType.STRING)
  private Audience audience;
  
  public User(String firstName, String lastName, String email, String passwordHash) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.passwordHash = passwordHash;
    this.audience = Audience.BROTHERS;
    this.roles = new HashSet<>();
    this.directPermissions = new HashSet<>();
  }

  @JsonIgnore
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

  public void addDirectPermission(Permission permission) {
    if (directPermissions == null) directPermissions = new HashSet<>();
    directPermissions.add(permission);
  }

  public boolean hasDirectPermission(Permission permission) {
    return directPermissions != null && directPermissions.contains(permission);
  }

  public boolean isVerified() {
    return verifiedAt != null;
  }
}
