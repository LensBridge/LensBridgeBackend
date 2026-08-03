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

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String image;

    @Column(nullable = false)
    private int duration;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    @Column(nullable = true)
    /**
     * Optional URL for the associated signup page. If provided, the frontend will show a QR Code linking to this URL.
     */
    private String signupUrl;
}
