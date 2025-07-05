package com.ibrasoft.lensbridge.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Document(collection = "events")
public class Event {
    @Id
    private UUID id;
    private String name;
    private LocalDate date;
    private EventStatus status;
}
