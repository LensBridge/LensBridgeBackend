--
-- V9: let the prayer-space actions actually be audited.
--
-- See the Postgres twin for what went wrong. Short version: V8 added the three
-- *_PRAYER_SPACE actions to AuditAction but not to audit_events' CHECK, so every audit insert
-- failed the constraint and AdminAuditService swallowed it -- deliberately, because a failed
-- audit write must not roll back the operation being audited. The edits went through and none
-- of them were recorded.
--
-- LINK_TICKET_EVENT and UNLINK_TICKET_EVENT are added here too. They were introduced alongside
-- V5's tCketManage link and have been failing the same constraint ever since, unnoticed for the
-- same reason.
--
-- SQLite cannot alter a CHECK constraint, so the table is rebuilt -- the same pattern as V4, V6,
-- V7 and V8. audit_events has no secondary indexes to recreate (the only entry in sqlite_master
-- is the implicit primary-key autoindex), and the SQLite twin of V1 declares no foreign key on
-- admin_id, so nothing else moves with it.
--
-- The Postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

create table audit_events_new (
    timestamp          timestamp    not null,
    admin_id           blob         not null,
    id                 blob         not null,
    target_entity_id   blob,
    details            varchar(512),
    action             varchar(255) not null check (action in (
        'APPROVE_UPLOAD','UNAPPROVE_UPLOAD','DELETE_UPLOAD','FEATURE_UPLOAD','UNFEATURE_UPLOAD',
        'CREATE_EVENT','UPDATE_EVENT','DELETE_EVENT',
        'CREATE_CALENDAR_EVENT','UPDATE_CALENDAR_EVENT','DELETE_CALENDAR_EVENT',
        'LINK_TICKET_EVENT','UNLINK_TICKET_EVENT',
        'CREATE_PRAYER_SPACE','UPDATE_PRAYER_SPACE','DELETE_PRAYER_SPACE',
        'CREATE_POSTER','UPDATE_POSTER','DELETE_POSTER',
        'CREATE_SOCIAL','UPDATE_SOCIAL','DELETE_SOCIAL',
        'SAVE_WEEKLY_CONTENT','DELETE_WEEKLY_CONTENT',
        'UPDATE_BOARD_CONFIG','UPDATE_BOARD_TICKER','REFRESH_BOARDS',
        'ISSUE_ENROLLMENT_TOKEN','REVOKE_DEVICE','ISSUE_DEVICE_COMMAND',
        'PROMOTE_USER','DEMOTE_USER','DISABLE_USER','ENABLE_USER',
        'VIEW_AUDIT_LOGS','EXPORT_DATA','SYSTEM_MAINTENANCE',
        'VERIFY_USER','UNVERIFY_USER','RESET_USER_PASSWORD','TRIGGER_PASSWORD_RESET_EMAIL',
        'ADD_USER_ROLE','REMOVE_USER_ROLE','GRANT_PERMISSION','REVOKE_PERMISSION',
        'ADD_USER','REMOVE_USER','UPDATE_USER')),
    ip_address         varchar(255),
    target_entity_type varchar(255) check (target_entity_type in (
        'USER','UPLOAD','MUSALLAH_BOARD','DEVICE','PRAYER_SPACE','EVENT')),
    user_agent         varchar(255),
    primary key (id)
);

insert into audit_events_new (timestamp, admin_id, id, target_entity_id, details, action,
                              ip_address, target_entity_type, user_agent)
select timestamp, admin_id, id, target_entity_id, details, action,
       ip_address, target_entity_type, user_agent
from audit_events;

drop table audit_events;

alter table audit_events_new rename to audit_events;
