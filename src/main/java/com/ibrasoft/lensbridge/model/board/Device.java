package com.ibrasoft.lensbridge.model.board;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code @DynamicUpdate}: the heartbeat path loads a device and saves it every 30 seconds per
 * board. Hibernate's default UPDATE writes every column, so an admin's revoke, rename or
 * audience change committed while a heartbeat was in flight would be written back over with
 * the heartbeat's stale copy. Updating only the columns that changed keeps the two apart.
 */
@Entity
@Table(name = "devices")
@DynamicUpdate
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

    // DeviceConfig points back at its device; without these exclusions the two @Data classes
    // call each other's toString/equals/hashCode until the stack overflows.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private DeviceConfig config;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    /** Ed25519 public key (32 bytes). Set during enrollment; null before. */
    @ToString.Exclude
    @Column(name = "public_key", length = 32)
    private byte[] publicKey;

    private String agentVersion;
    private String hardwareModel;
    private String lastSeenIp;

    /** The board's latest report of what it runs and shows (heartbeat), and when it came. */
    @Convert(converter = BoardReportConverter.class)
    @Column(name = "board_report", columnDefinition = "TEXT")
    private BoardReport boardReport;
    private Instant boardReportAt;

    /** When set, all auth attempts are rejected and any live session is closed. */
    private Instant revokedAt;

    @PrePersist
    private void prePersist() {
        if (enrolledAt == null) enrolledAt = Instant.now();
        if (organizationId == null) organizationId = "utmmsa";
    }

}
