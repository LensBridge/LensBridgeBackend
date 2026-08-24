package com.ibrasoft.lensbridge.model.minbar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.ibrasoft.tcketmanagebackend.model.event.Event;

@Entity
@Table(name = "board_events")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BoardEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String location;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Builder.Default
    @Column(nullable = false)
    private Boolean allDay = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    @Builder.Default
    @Column(nullable = false)
    private Boolean allowUploads = false;

    /**
     * Reference to a tCketEvent object, if it exists, for tCketManage integration
     */
    @OneToOne
    @JoinColumn(name = "event_id")
    // hacky fix for OSIV in the meantime, until I
    // fix it once and for all in tCketManage
    @JsonIgnoreProperties({"zones", "tickets"})
    private Event event;
}
