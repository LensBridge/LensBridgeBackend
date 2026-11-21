package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentTokenRepository extends JpaRepository<EnrollmentToken, UUID> {

    Optional<EnrollmentToken> findByTokenHash(byte[] tokenHash);

    /**
     * Single-use gate: stamps the token consumed only if nobody has yet and it has not
     * expired. The row count is the answer, 1 for the one winner and 0 for everyone else.
     * The persistence context is cleared afterwards so the already-loaded copy (which still
     * says "unconsumed") cannot be flushed back over the update.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EnrollmentToken t set t.consumedAt = :now "
            + "where t.id = :id and t.consumedAt is null and t.expiresAt > :now")
    int markConsumed(@Param("id") UUID id, @Param("now") Instant now);

    /** Points a consumed token at the device it created. */
    @Modifying
    @Query("update EnrollmentToken t set t.consumedByDeviceId = :deviceId where t.id = :id")
    int recordConsumer(@Param("id") UUID id, @Param("deviceId") UUID deviceId);
}
