package com.ibrasoft.lensbridge.model.audit;

public enum AuditEntityType {
    USER("User"),
    UPLOAD("Upload"),
    MUSALLAH_BOARD("Musallah Board"),
    DEVICE("Device"),
    /** A prayer space and its walking directions. */
    PRAYER_SPACE("Prayer Space"),
    EVENT("Event");


    private final String description;

    AuditEntityType(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.name();
    }
}