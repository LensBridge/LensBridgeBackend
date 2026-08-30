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

  /**
   * Generation counter for this account's access tokens.
   * <p>
   * Every JWT carries the value that was current when it was minted; {@code AuthTokenFilter}
   * compares the claim against this column on each request and rejects anything stale. Bumping
   * it is therefore the only way to kill an access token before its 15-minute expiry — refresh
   * tokens live in their own table and are revoked separately, but a JWT is self-contained and
   * would otherwise stay usable after the victim changed their password precisely to stop it.
   * <p>
   * Not a security boundary on its own: it is a revocation counter, not a secret, and it only
   * ever moves forward. Wrapping is not a concern at {@code long} width.
   */
  @JsonIgnore
  @Column(nullable = false)
  private long tokenVersion = 0L;

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

  /**
   * Sets the stored hash and invalidates every access token minted against the old one.
   * <p>
   * The bump lives here rather than in the callers because this is the single chokepoint every
   * credential change goes through — self-service change-password, the emailed reset link, and
   * the admin-initiated reset all land on this setter. A caller that forgot to bump would leave
   * a 15-minute window in which the old password's tokens still work, which is the whole bug
   * this counter exists to close.
   * <p>
   * JPA hydration writes the field directly, not through this setter, so loading a row does not
   * move the counter.
   */
  public void setPassword(String passwordHash) {
    this.passwordHash = passwordHash;
    incrementTokenVersion();
  }

  /**
   * Invalidates every access token issued for this account so far. The caller must persist the
   * entity for it to take effect.
   */
  public void incrementTokenVersion() {
    this.tokenVersion++;
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
