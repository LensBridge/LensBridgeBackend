package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SqliteDataJpaTest
class DeviceRepositoryTest {

    @Autowired
    private DeviceRepository repository;
    @Autowired
    private EntityManager em;

    /**
     * A heartbeat loads the device, touches lastHeartbeat and saves. If an admin revokes the
     * device in between, the heartbeat's flush must not write the stale (null) revokedAt back.
     */
    @Test
    void aHeartbeatFlushDoesNotUndoARevokeCommittedMeanwhile() {
        Device saved = repository.saveAndFlush(Device.builder()
                .displayName("Lobby")
                .audience(Audience.BOTH)
                .build());
        em.clear();

        Device heartbeatCopy = repository.findById(saved.getId()).orElseThrow();

        // Another transaction revokes the device; the heartbeat's copy still says "not revoked".
        em.createQuery("update Device d set d.revokedAt = :at where d.id = :id")
                .setParameter("at", Instant.now())
                .setParameter("id", saved.getId())
                .executeUpdate();

        heartbeatCopy.setLastHeartbeat(Instant.now());
        repository.saveAndFlush(heartbeatCopy);
        em.clear();

        Device reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getLastHeartbeat()).isNotNull();
    }

    /** A board created in the console: no key, no enrollment time; the claim columns from V5 round-trip. */
    @Test
    void aPreCreatedDeviceHasNoKeyAndKeepsItsClaimSecret() {
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, (byte) 7);
        Device saved = repository.saveAndFlush(Device.builder()
                .displayName("North Musallah")
                .audience(Audience.BOTH)
                .claimSecret(secret)
                .provisionSequence(1795089600250L)
                .build());
        em.clear();

        Device reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPublicKey()).isNull();
        assertThat(reloaded.getEnrolledAt()).isNull();
        assertThat(reloaded.getClaimSecret()).isEqualTo(secret);
        assertThat(reloaded.getProvisionSequence()).isEqualTo(1795089600250L);
        assertThat(reloaded.toString()).doesNotContain("claimSecret");
    }
}
