package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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
}
