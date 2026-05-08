package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "board_configs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BoardConfig {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "board_location", length = 50)
    private BoardLocation boardLocation;

    @Embedded
    private Location location;

    private int posterCycleIntervalMs;
    private int refreshAfterIshaMinutes;
    private boolean darkModeAfterIsha;
    private int darkModeAfterIshaMinutes;
    private boolean enableScrollingMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "board_config_messages",
        joinColumns = @JoinColumn(name = "board_location"))
    @Column(name = "message")
    private List<String> scrollingMessages;
}
