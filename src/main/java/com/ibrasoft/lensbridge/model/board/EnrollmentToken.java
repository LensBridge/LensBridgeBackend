package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time pairing token issued by an admin and consumed by an agent during enrollment.
 * Plaintext is shown to the operator once at issue time; only its SHA-256 hash is persisted.
 */
@Entity
@Table(name = "enrollment_tokens", indexes = {
        @Index(name = "idx_enrollment_tokens_hash", columnList = "token_hash", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 of the plaintext token (32 bytes). */
    @Column(name = "token_hash", length = 32, nullable = false)
    private byte[] tokenHash;

    /** Display name to assign to the resulting Device. */
    @Column(nullable = false)
    private String displayName;

    /** Audience baked in at issue time so the operator can't typo it on the Pi. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    /** Username of the admin that issued the token (from JWT). */
    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Set the moment the token is consumed; subsequent attempts fail. */
    private Instant consumedAt;

    /** Device created from this token, if consumed. */
    private UUID consumedByDeviceId;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
