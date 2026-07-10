package com.ibrasoft.minbar.repository.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.minbar.model.auth.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Boolean existsByEmail(String email);

  Boolean existsByStudentNumber(String studentNumber);
}
