package com.bezkoder.springjwt.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.bezkoder.springjwt.models.User;

@Repository
public interface UserRepository extends MongoRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByVerificationToken(String verificationToken);

  Boolean existsByEmail(String email);

  Boolean existsByStudentNumber(String studentNumber);
}
