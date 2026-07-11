package com.ibrasoft.minbar.signage.model.embedded;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.minbar.signage.model.Device;
import com.ibrasoft.minbar.signage.model.Location;
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

    private int posterCycleIntervalMs;
    private int refreshAfterIshaMinutes;
    private boolean darkModeAfterIsha;
    private int darkModeAfterMaghribMinutes;
    private boolean enableScrollingMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "board_config_messages",
        joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "message")
    private List<String> scrollingMessages;
}
