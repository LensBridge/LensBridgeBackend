package com.ibrasoft.minbar.signage.repository;

import com.ibrasoft.minbar.signage.model.EnrollmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentTokenRepository extends JpaRepository<EnrollmentToken, UUID> {

    Optional<EnrollmentToken> findByTokenHash(byte[] tokenHash);
}
