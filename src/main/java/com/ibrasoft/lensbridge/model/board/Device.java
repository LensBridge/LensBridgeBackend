package com.ibrasoft.lensbridge.model.board;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.persistence.*;
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

    // If you ever add multi-tenancy, remove the default and make this required
    // Hardcoded for now because we only have one organization
    @Builder.Default
    @Column(nullable = false)
    private String organizationId = "utmmsa";
    
    @Column(nullable = false)
    private String displayName;

    private Instant enrolledAt;
    private Instant lastHeartbeat;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DeviceConfig config;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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
        if (enrolledAt == null) enrolledAt = Instant.now();
        if (organizationId == null) organizationId = "utmmsa";
    }

}
