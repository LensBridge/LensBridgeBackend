package com.ibrasoft.minbar.audit.model;

public enum AuditEntityType {
    USER("User"),
    UPLOAD("Upload"),
    MUSALLAH_BOARD("Musallah Board"),
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