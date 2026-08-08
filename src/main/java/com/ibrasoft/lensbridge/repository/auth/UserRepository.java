package com.ibrasoft.lensbridge.repository.auth;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  /**
   * Loads {@code directPermissions} on its own rather than eagerly with the entity, so
   * the two collection tables are never joined in the same query. See the field comment
   * on {@link User#getDirectPermissions()}.
   */
  @Query("select p from User u join u.directPermissions p where u.id = :userId")
  Set<Permission> findDirectPermissions(@Param("userId") UUID userId);

  /** Ids of users holding {@code role}. Used to check nobody is about to be locked out. */
  @Query("select u.id from User u join u.roles r where r in :roles")
  Set<UUID> findIdsByRoleIn(@Param("roles") Collection<Role> roles);

  /** Ids of users holding {@code permission} as a direct grant, ignoring anything roles confer. */
  @Query("select u.id from User u join u.directPermissions p where p = :permission")
  Set<UUID> findIdsByDirectPermission(@Param("permission") Permission permission);

  Boolean existsByEmail(String email);

  Boolean existsByStudentNumber(String studentNumber);
}
