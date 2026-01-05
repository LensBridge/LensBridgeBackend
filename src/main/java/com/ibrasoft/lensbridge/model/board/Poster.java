package com.ibrasoft.lensbridge.model.board;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "posters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Poster {
    @Id
    public UUID id;

    private String title;

    private String image;

    private int duration;

    /**
     * The date from which the poster becomes active (inclusive)
     */
    private LocalDate startDate;

    /**
     * The date until which the poster remains active (exclusive)
     */
    private LocalDate endDate;

    private Audience audience;
}
