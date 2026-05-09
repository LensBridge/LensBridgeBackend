package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log + delivery queue for admin-issued commands targeted at a single device.
 * <p>
 * The polymorphic command shape (kind + payload) is captured here as a discriminator
 * string and a JSON blob, so adding new command kinds does not require schema changes.
 */
@Entity
@Table(name = "device_commands", indexes = {
        @Index(name = "idx_device_commands_device_status",
               columnList = "device_id, status"),
        @Index(name = "idx_device_commands_pending",
               columnList = "device_id, issued_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    /** Polymorphic discriminator (e.g. "chrome.reload"). */
    @Column(nullable = false)
    private String kind;

    /** Serialized CommandPayload subclass (Jackson). */
    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    /** Username of the admin that issued the command (from JWT). */
    @Column(nullable = false)
    private String issuedBy;

    /** Maximum execution time as supplied by the issuer; null = server default. */
    private Integer deadlineMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceCommandStatus status;

    @Column(nullable = false)
    private Instant issuedAt;

    private Instant deliveredAt;
    private Instant ackedAt;
    private Instant startedAt;
    private Instant finishedAt;

    /** Free-form result payload from the agent (JSON). */
    @Column(columnDefinition = "TEXT")
    private String outputJson;

    /** Short error message when status is FAILED / TIMEOUT / REJECTED / EXPIRED. */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    private void prePersist() {
        if (issuedAt == null) issuedAt = Instant.now();
        if (status == null) status = DeviceCommandStatus.PENDING;
    }
}
