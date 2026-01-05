package com.ibrasoft.lensbridge.model.board;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    private UUID id;

    /**
     * Event name/title
     */
    private String name;

    private String description;

    private String location;

    private long startTimestamp;

    private long endTimestamp;

    private Boolean allDay;

    private Audience audience;
}
