package com.ibrasoft.lensbridge.model.auth;

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

@AllArgsConstructor
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
  private String passwordHash;

  @ElementCollection(targetClass = Role.class)
  @Enumerated(EnumType.STRING)
  @CollectionTable(name = "user_roles")
  private Set<Role> roles;

  private boolean verified;
}
