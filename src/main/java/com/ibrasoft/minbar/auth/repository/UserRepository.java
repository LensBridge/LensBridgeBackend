package com.ibrasoft.minbar.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.minbar.auth.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Boolean existsByEmail(String email);

  Boolean existsByStudentNumber(String studentNumber);
}
