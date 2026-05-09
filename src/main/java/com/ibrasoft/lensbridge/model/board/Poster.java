package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posters")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Poster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String title;
    private String image;
    private int duration;
    private Instant startTime;
    private Instant endTime;
    @Enumerated(EnumType.STRING)
    private Audience audience;
}
