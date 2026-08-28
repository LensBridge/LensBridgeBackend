--
-- V9: let the prayer-space actions actually be audited.
--
-- V8 added CREATE_PRAYER_SPACE / UPDATE_PRAYER_SPACE / DELETE_PRAYER_SPACE to AuditAction and
-- wired them into the admin controller, but did not widen audit_events' CHECK constraints. The
-- inserts therefore failed the constraint, and AdminAuditService catches and logs rather than
-- rethrows -- by design, since a failed audit write must not roll back the operation the user
-- asked for. The result was the worst version of both: the writes succeeded and none of them
-- were recorded. Caught by inspecting the table after exercising the endpoints; nothing in the
-- API surface showed it.
--
-- target_entity_type gains PRAYER_SPACE for the same reason. Without it AdminAuditService's
-- toEntityType() falls through to EVENT, which would have filed prayer-space edits under the
-- same heading as calendar events even once the action constraint allowed the row.
--
-- MigrationEnumParityTest now fails the build when an enum constant is not named anywhere in
-- either migration directory, so the next value to be added cannot repeat this silently.
--
-- The SQLite twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select con.conname
        from pg_constraint con
        join pg_class rel on rel.oid = con.conrelid
        where rel.relname = 'audit_events'
          and con.contype = 'c'
    loop
        execute format('alter table audit_events drop constraint %I', constraint_name);
    end loop;
end $$;

alter table audit_events
    add constraint audit_events_action_check check (action in (
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
        'ADD_USER','REMOVE_USER','UPDATE_USER'));

alter table audit_events
    add constraint audit_events_target_entity_type_check check (target_entity_type in (
        'USER','UPLOAD','MUSALLAH_BOARD','DEVICE','PRAYER_SPACE','EVENT'));
