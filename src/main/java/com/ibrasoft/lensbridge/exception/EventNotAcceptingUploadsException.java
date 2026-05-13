package com.ibrasoft.lensbridge.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class EventNotAcceptingUploadsException extends RuntimeException {
    private final UUID eventId;

    public EventNotAcceptingUploadsException(UUID eventId) {
        super("Event " + eventId + " is not currently accepting uploads");
        this.eventId = eventId;
    }
}
