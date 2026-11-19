package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /** Every board that is not revoked, by name: the "all boards" USB kit. */
    List<Device> findByRevokedAtIsNullOrderByDisplayNameAsc();

    /**
     * Binds a key to a device that has none. The row count is the answer: 1 for the one
     * caller that bound it, 0 when the device already had a key (or does not exist), so two
     * boards claiming at once cannot both win. The persistence context is cleared afterwards
     * so a copy loaded before the update is not flushed back over it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Device d set d.publicKey = :key where d.id = :id and d.publicKey is null")
    int bindPublicKeyIfUnset(@Param("id") UUID id, @Param("key") byte[] key);

    /**
     * Gives a device that is not revoked its next setup-file sequence: one more than the last,
     * or {@code now} (epoch milliseconds) when that is greater. 1 when it did, 0 for an
     * unknown or revoked device.
     * <p>
     * One UPDATE, not a read and a save, so it takes the row's write lock (the database's on
     * SQLite) before it reads the old value: a second issuer waits for the first to commit
     * and then counts from its sequence, never from the same one. Make it the transaction's
     * first statement: a SQLite transaction that has already read cannot wait for the lock
     * and fails at once instead. Read the sequence back afterwards ({@code findById}; the
     * persistence context is cleared).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Device d set d.provisionSequence = case"
            + " when coalesce(d.provisionSequence, 0) + 1 > :now then coalesce(d.provisionSequence, 0) + 1"
            + " else :now end"
            + " where d.id = :id and d.revokedAt is null")
    int advanceProvisionSequence(@Param("id") UUID id, @Param("now") long now);

    /**
     * Replaces a device's claim secret. Call it after {@link #advanceProvisionSequence} in the
     * same transaction, which already holds the row's lock, so the stored secret is always the
     * one of the highest sequence issued.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Device d set d.claimSecret = :secret where d.id = :id")
    int replaceClaimSecret(@Param("id") UUID id, @Param("secret") byte[] secret);
}
