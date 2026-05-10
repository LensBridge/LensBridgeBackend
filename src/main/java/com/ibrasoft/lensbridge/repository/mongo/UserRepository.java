package com.ibrasoft.lensbridge.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.ibrasoft.lensbridge.model.auth.User;

@Repository
public interface UserRepository extends MongoRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByVerificationToken(String verificationToken);

  Boolean existsByEmail(String email);

  Boolean existsByStudentNumber(String studentNumber);
}
