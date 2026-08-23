package com.ibrasoft.lensbridge.model.audit;

import lombok.Getter;

@Getter
public enum AuditAction {
    // Upload Management Actions
    APPROVE_UPLOAD("Approve Upload"),
    UNAPPROVE_UPLOAD("Remove Upload Approval"),
    DELETE_UPLOAD("Delete Upload"),
    FEATURE_UPLOAD("Feature Upload"),
    UNFEATURE_UPLOAD("Remove Featured Status"),
    
    // Event Management Actions
    CREATE_EVENT("Create Event"),
    UPDATE_EVENT("Update Event"),
    DELETE_EVENT("Delete Event"),

    // Calendar Event Management Actions (Musallah Board)
    CREATE_CALENDAR_EVENT("Create Calendar Event"),
    UPDATE_CALENDAR_EVENT("Update Calendar Event"),
    DELETE_CALENDAR_EVENT("Delete Calendar Event"),
    LINK_TICKET_EVENT("Link Ticket Event to Board Event"),
    UNLINK_TICKET_EVENT("Unlink Ticket Event from Board Event"),

    // Poster/Frame Management Actions (Musallah Board)
    CREATE_POSTER("Create Poster"),
    UPDATE_POSTER("Update Poster"),
    DELETE_POSTER("Delete Poster"),

    // Promotable Social Media Actions
    CREATE_SOCIAL("Create Promotable Social Media"),
    UPDATE_SOCIAL("Update Promotable Social Media"),
    DELETE_SOCIAL("Delete Promotable Social Media"),

    // Board Content Actions (Musallah Board)
    // SAVE_WEEKLY_CONTENT is the only record of who last changed a jummah time, since
    // quotes and prayer times share one permission and one endpoint.
    SAVE_WEEKLY_CONTENT("Save Weekly Content"),
    DELETE_WEEKLY_CONTENT("Delete Weekly Content"),
    UPDATE_BOARD_CONFIG("Update Board Config"),
    UPDATE_BOARD_TICKER("Update Board Ticker"),
    REFRESH_BOARDS("Refresh All Boards"),

    // Device Fleet Actions (Musallah Board)
    ISSUE_ENROLLMENT_TOKEN("Issue Device Enrollment Token"),
    REVOKE_DEVICE("Revoke Device"),
    ISSUE_DEVICE_COMMAND("Issue Device Command"),


    // User Management Actions
    PROMOTE_USER("Promote User to Admin"),
    DEMOTE_USER("Remove Admin Role"),
    DISABLE_USER("Disable User Account"),
    ENABLE_USER("Enable User Account"),
    
    // System Actions
    VIEW_AUDIT_LOGS("View Audit Logs"),
    EXPORT_DATA("Export Data"),
    SYSTEM_MAINTENANCE("System Maintenance"),

    // User Management 
    VERIFY_USER("Verify User"),
    UNVERIFY_USER("Unverify User"),
    RESET_USER_PASSWORD("Reset User Password"),
    TRIGGER_PASSWORD_RESET_EMAIL("Trigger Password Reset Email"),

    ADD_USER_ROLE("Add User Role"),
    REMOVE_USER_ROLE("Remove User Role"),
    GRANT_PERMISSION("Grant Direct Permission"),
    REVOKE_PERMISSION("Revoke Direct Permission"),

    ADD_USER("Add User"),
    REMOVE_USER("Remove User"),
    UPDATE_USER("Update User");
    
    private final String description;
    
    AuditAction(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
