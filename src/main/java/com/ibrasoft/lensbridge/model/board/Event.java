package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "board_events")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String description;
    private String location;
    private Long startEpochMs;
    private Long endEpochMs;
    private Boolean allDay;
    @Enumerated(EnumType.STRING)
    private Audience audience;
}
