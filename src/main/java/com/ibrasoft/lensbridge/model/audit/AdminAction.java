package com.ibrasoft.lensbridge.model.audit;

import lombok.Getter;

@Getter
public enum AdminAction {
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

    ADD_USER("Add User"),
    REMOVE_USER("Remove User"),
    UPDATE_USER("Update User");
    
    private final String description;
    
    AdminAction(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
