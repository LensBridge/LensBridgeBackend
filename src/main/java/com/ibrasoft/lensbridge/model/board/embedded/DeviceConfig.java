package com.ibrasoft.lensbridge.model.board.embedded;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "board_configs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceConfig {
    @Id
    private UUID id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "device_id")
    private Device device;

    @Embedded
    private Location location;

    private boolean darkModeAfterIsha;
    private boolean enableScrollingMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "board_config_messages",
        joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "message")
    private List<String> scrollingMessages;

    /**
     * Destination for the "Stay Connected" QR code on the closing slide. Optional:
     * a board with no socialUrl renders that slide without a QR.
     *
     * Deliberately nullable. NOT NULL could not be satisfied here -- devices are
     * enrolled with a default config that does not set this
     * (DeviceEnrollmentService), and rows created before the column exists have
     * no value to backfill from.
     */
    @Column(name = "social_url")
    private String socialUrl;
}
