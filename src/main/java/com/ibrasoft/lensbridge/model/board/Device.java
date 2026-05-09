package com.ibrasoft.lensbridge.model.board;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // TODO: If you ever add multi-tenancy, actually use this
    // Hardcoded for now because we only have one organization
    private String organizationId = "utmmsa";
    @NotBlank
    private String displayName;

    private Instant enrolledAt;
    private Instant lastHeartbeat;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DeviceConfig config;

    private Audience audience;

    /** Ed25519 public key (32 bytes). Set during enrollment; null before. */
    @Column(name = "public_key", length = 32)
    private byte[] publicKey;

    private String agentVersion;
    private String hardwareModel;
    private String lastSeenIp;

    /** When set, all auth attempts are rejected and any live session is closed. */
    private Instant revokedAt;

    @PrePersist
    private void prePersist() {
        enrolledAt = Instant.now();
    }

}
